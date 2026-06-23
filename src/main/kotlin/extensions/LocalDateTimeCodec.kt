package extensions

import kotlinx.datetime.*
import org.bson.codecs.Codec
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

class LocalDateTimeCodec : Codec<LocalDateTime> {
    override fun encode(writer: BsonWriter, value: LocalDateTime, encoderContext: EncoderContext) {
        // Сохраняем как BSON DateTime (миллисекунды с эпохи)
        val instant = value.toInstant(TimeZone.UTC)
        writer.writeDateTime(instant.toEpochMilliseconds())
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): LocalDateTime {
        // Читаем как BSON DateTime
        val millis = reader.readDateTime()
        val instant = Instant.fromEpochMilliseconds(millis)
        return instant.toLocalDateTime(TimeZone.UTC)
    }

    override fun getEncoderClass(): Class<LocalDateTime> = LocalDateTime::class.java
}