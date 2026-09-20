import CONST_PAGE_SIZE_MAX
import base.repository.PageRequest
import org.junit.Test

/**
 * Арифметика постраничного вывода. Mongo не нужна - [PageRequest] чистый.
 */
class PagingTest {

    @Test
    fun a_page_skips_whole_pages_and_not_single_documents() {
        // Ровно та ошибка, из-за которой вторая страница начиналась
        // со второго документа, а не с двадцать первого
        assert(PageRequest.of(0, 20).skip == 0) { "got ${PageRequest.of(0, 20).skip}" }
        assert(PageRequest.of(1, 20).skip == 20) { "got ${PageRequest.of(1, 20).skip}" }
        assert(PageRequest.of(3, 20).skip == 60) { "got ${PageRequest.of(3, 20).skip}" }
    }

    @Test
    fun pages_do_not_overlap_and_do_not_leave_gaps() {
        val size = 7
        val ranges = (0..4).map { page ->
            val request = PageRequest.of(page, size)
            request.skip until request.skip + request.size
        }

        ranges.zipWithNext { current, next ->
            assert(current.last + 1 == next.first) { "разрыв или нахлёст между $current и $next" }
        }
    }

    @Test
    fun a_negative_page_is_the_first_one() {
        val request = PageRequest.of(-5, 20)

        assert(request.page == 0) { "got ${request.page}" }
        assert(request.skip == 0) { "got ${request.skip}" }
    }

    @Test
    fun a_page_size_stays_within_its_limits() {
        assert(PageRequest.of(0, 0).size == 1) { "got ${PageRequest.of(0, 0).size}" }
        assert(PageRequest.of(0, -10).size == 1) { "got ${PageRequest.of(0, -10).size}" }
        assert(PageRequest.of(0, CONST_PAGE_SIZE_MAX + 500).size == CONST_PAGE_SIZE_MAX) {
            "одним запросом нельзя вычитать всю коллекцию"
        }
        assert(PageRequest.of(0, 33).size == 33) { "разрешённый размер трогать нельзя" }
    }

    @Test
    fun a_huge_page_number_does_not_overflow_into_a_negative_offset() {
        // page * size не помещается в Int, а отрицательный skip драйвер не примет
        val request = PageRequest.of(Int.MAX_VALUE, CONST_PAGE_SIZE_MAX)

        assert(request.skip > 0) { "got ${request.skip}" }
        assert(request.skip == Int.MAX_VALUE) { "got ${request.skip}" }
    }

    @Test
    fun the_number_of_pages_covers_every_document() {
        val request = PageRequest.of(0, 20)

        assert(request.totalPages(0) == 0) { "пустой коллекции страниц не нужно" }
        assert(request.totalPages(1) == 1) { "got ${request.totalPages(1)}" }
        assert(request.totalPages(20) == 1) { "got ${request.totalPages(20)}" }
        assert(request.totalPages(21) == 2) { "хвост в один документ - это ещё одна страница" }
        assert(request.totalPages(40) == 2) { "got ${request.totalPages(40)}" }
    }

    @Test
    fun the_applied_values_are_the_ones_reported_back() {
        // Клиент попросил невозможное и должен увидеть, что применилось на самом деле
        val request = PageRequest.of(-1, CONST_PAGE_SIZE_MAX * 2)

        assert(request.page == 0) { "got ${request.page}" }
        assert(request.size == CONST_PAGE_SIZE_MAX) { "got ${request.size}" }
    }
}
