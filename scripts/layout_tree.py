#!/usr/bin/env python3
"""
Раскладка дерева навыков без наложений (1.37.0): связи не пересекаются, не проходят сквозь чужие узлы, узлы не
теснятся и не сливаются. Берёт координаты `tree.json` как черновик, раздвигает их и чинит нарушения, двигая узлы по
одному, с наименьшим уходом от черновика. Правила — те же, что проверяет `TreeLayoutTest` сервера.

    python3 scripts/layout_tree.py [--spread 1.0] [--seed 7]    # --spread > 1 раздвигает всё дерево перед починкой
"""
import argparse
import json
import math
import random
from pathlib import Path

TREE = Path(__file__).resolve().parent.parent / "src/main/resources/content/tree.json"

# Радиус узла на карте в единицах дерева (как при наибольшем приближении клиента) и зазоры — копия `TreeLayoutTest`.
RADIUS = {"SMALL": 15, "ATTRIBUTE": 16, "NOTABLE": 25, "MASTERY": 25, "JEWEL_SOCKET": 25, "START": 30, "KEYSTONE": 33}
EDGE_GAP = 12    # связь проходит от чужого узла не ближе его радиуса и этого зазора
NODE_GAP = 40    # между краями двух узлов
MIN_ANGLE = 12   # градусов между двумя связями одного узла


def cross(a, b, c, d):
    def ccw(p, q, r):
        return (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])
    return ccw(a, b, c) * ccw(a, b, d) < 0 and ccw(c, d, a) * ccw(c, d, b) < 0


def seg_dist(p, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    t = max(0.0, min(1.0, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / (dx * dx + dy * dy or 1)))
    return math.hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dy)


class Layout:
    def __init__(self, nodes, spread):
        self.code = [n["code"] for n in nodes]
        at = {c: i for i, c in enumerate(self.code)}
        self.r = [RADIUS[n["type"]] for n in nodes]
        self.home = [(n.get("x", 0) * spread, n.get("y", 0) * spread) for n in nodes]
        self.p = list(self.home)
        edges = {tuple(sorted((at[n["code"]], at[c]))) for n in nodes for c in n.get("connections", [])}
        self.edges = sorted(edges)
        self.adj = [[] for _ in nodes]
        for k, (a, b) in enumerate(self.edges):
            self.adj[a].append(k)
            self.adj[b].append(k)

    def edge_cost(self, k, skip=None):
        """Нарушения одной связи: пересечения с другими и чужие узлы на её пути."""
        a, b = self.edges[k]
        pa, pb = self.p[a], self.p[b]
        cost = 0
        for j, (c, d) in enumerate(self.edges):
            if j == k or j == skip or c in (a, b) or d in (a, b):
                continue
            if cross(pa, pb, self.p[c], self.p[d]):
                cost += 1000
        for i, q in enumerate(self.p):
            if i != a and i != b and seg_dist(q, pa, pb) < self.r[i] + EDGE_GAP:
                cost += 800
        return cost

    def node_cost(self, i):
        q, cost = self.p[i], 0.0
        for k in self.adj[i]:
            cost += self.edge_cost(k)
        for k, (a, b) in enumerate(self.edges):
            if i not in (a, b) and seg_dist(q, self.p[a], self.p[b]) < self.r[i] + EDGE_GAP:
                cost += 800
        for j, o in enumerate(self.p):
            if j != i:
                gap = math.hypot(q[0] - o[0], q[1] - o[1]) - self.r[i] - self.r[j] - NODE_GAP
                if gap < 0:
                    cost += 400 - gap * 10
        cost += self.angle_cost(i) + sum(self.angle_cost(j) for j in self.neighbours(i))
        cost += math.hypot(q[0] - self.home[i][0], q[1] - self.home[i][1]) * .5
        return cost

    def neighbours(self, i):
        return [b if a == i else a for a, b in (self.edges[k] for k in self.adj[i])]

    def angle_cost(self, i):
        q = self.p[i]
        angles = sorted(math.degrees(math.atan2(o[1] - q[1], o[0] - q[0])) for o in (self.p[j] for j in self.neighbours(i)))
        if len(angles) < 2:
            return 0
        gaps = [b - a for a, b in zip(angles, angles[1:])] + [angles[0] + 360 - angles[-1]]
        return sum(600 for g in gaps if g < MIN_ANGLE)

    def violations(self):
        bad = set()
        for k, (a, b) in enumerate(self.edges):
            for j in range(k + 1, len(self.edges)):
                c, d = self.edges[j]
                if len({a, b, c, d}) == 4 and cross(self.p[a], self.p[b], self.p[c], self.p[d]):
                    bad |= {a, b, c, d}
            for i, q in enumerate(self.p):
                if i != a and i != b and seg_dist(q, self.p[a], self.p[b]) < self.r[i] + EDGE_GAP:
                    bad |= {i, a, b}
        for i in range(len(self.p)):
            for j in range(i + 1, len(self.p)):
                if math.dist(self.p[i], self.p[j]) < self.r[i] + self.r[j] + NODE_GAP:
                    bad |= {i, j}
            if self.angle_cost(i):
                bad |= {i, *self.neighbours(i)}
        return bad

    def group_of(self, i):
        """Кластер узла: узлы с тем же кодом без последнего звена (`MAR_C2_3` - `MAR_C2_*`)."""
        stem = self.code[i].rsplit("_", 1)[0]
        return [j for j, c in enumerate(self.code) if c.rsplit("_", 1)[0] == stem and c != "SCION_START"]

    def move_group(self, group, rng, tries):
        """Кластер поворачивается вокруг центра дерева и отходит по лучу целиком: так чинится кластер не в своём секторе."""
        start = [self.p[j] for j in group]
        cost = lambda: sum(self.node_cost(j) for j in group)
        best, best_cost = start, cost()
        for _ in range(tries):
            turn, stretch = math.radians(rng.uniform(-25, 25)), rng.uniform(.9, 1.12)
            c, s_ = math.cos(turn), math.sin(turn)
            moved = [((x * c - y * s_) * stretch, (x * s_ + y * c) * stretch) for x, y in start]
            for j, q in zip(group, moved):
                self.p[j] = q
            c_ = cost()
            if c_ < best_cost:
                best, best_cost = moved, c_
        for j, q in zip(group, best):
            self.p[j] = q

    def sweep(self, i, reach=320, step=16):
        """Узел, застрявший в ловушке малых шагов, ищет лучшее место по всей сетке вокруг себя."""
        start, best, best_cost = self.p[i], self.p[i], self.node_cost(i)
        for dx in range(-reach, reach + 1, step):
            for dy in range(-reach, reach + 1, step):
                self.p[i] = (start[0] + dx, start[1] + dy)
                c = self.node_cost(i)
                if c < best_cost:
                    best, best_cost = self.p[i], c
        self.p[i] = best

    def repair(self, rng, rounds=60, tries=24):
        for rnd in range(rounds):
            bad = sorted(self.violations())
            print(f"round {rnd}: {len(bad)} nodes in trouble")
            if not bad:
                return True
            rng.shuffle(bad)
            if rnd % 10 == 9:
                for i in bad:
                    if self.code[i] != "SCION_START":
                        self.sweep(i)
                continue
            reach = 40 + 20 * (rnd // 5)
            for i in bad:
                if self.code[i] == "SCION_START":
                    continue
                best, best_cost = self.p[i], self.node_cost(i)
                start = self.p[i]
                for _ in range(tries):
                    a, d = rng.uniform(0, 2 * math.pi), rng.uniform(.2, 1) * reach
                    self.p[i] = (start[0] + d * math.cos(a), start[1] + d * math.sin(a))
                    c = self.node_cost(i)
                    if c < best_cost:
                        best, best_cost = self.p[i], c
                self.p[i] = best
            if rnd % 3 == 2:
                for stem in {self.code[i].rsplit("_", 1)[0] for i in bad}:
                    group = self.group_of(next(i for i in bad if self.code[i].rsplit("_", 1)[0] == stem))
                    if 1 < len(group) <= 12:
                        self.move_group(group, rng, tries)
        return not self.violations()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--spread", type=float, default=1.0)
    ap.add_argument("--seed", type=int, default=7)
    args = ap.parse_args()
    doc = json.loads(TREE.read_text())
    layout = Layout(doc["nodes"], args.spread)
    ok = layout.repair(random.Random(args.seed))
    for n, (x, y) in zip(doc["nodes"], layout.p):
        n.pop("x", None), n.pop("y", None)
        if round(x): n["x"] = round(x)
        if round(y): n["y"] = round(y)
    TREE.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
    print("clean" if ok else "violations left")


if __name__ == "__main__":
    main()
