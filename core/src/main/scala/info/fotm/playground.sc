import java.io.Serializable

import com.github.nscala_time.time.Imports._
import com.twitter.bijection.Bijection
import info.fotm.aether.Storage.PersistedStorageState
import info.fotm.aether.{StorageAxisState, Storage, PersistedAxisState}
import info.fotm.api.{BattleNetAPI, BattleNetAPISettings}
import info.fotm.api.models.{Region, Twos, US, Leaderboard}
import info.fotm.domain._
import info.fotm.util.{S3Persisted, Compression}
import scodec._
import scodec.bits._
import codecs._
import scodec.codecs.implicits._
import shapeless.Lazy
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import com.github.nscala_time.time.Imports._
//val bnet = new BattleNetAPI(US, "vntnwpsguf4pqak7e8y7tgn35795fqfj")
//val lb = Await.result(bnet.WoW.leaderboard(Twos), Duration.Inf)
//val serialized = Codec.encode(lb).require.toByteArray
//val gzipped = Bijection.bytes2GzippedBytes(serialized)
val bucket = "fotm-info-staging-bucket"
val path = "storage.txt"
val p = new S3Persisted[PersistedStorageState](bucket, path)(Storage.serializer)
val state: PersistedStorageState = p.fetch().get

object MyCodecImplicits {
  implicit def seqCodec[A](implicit listCodec: Codec[List[A]]): Codec[Seq[A]] =
    listCodec.xmap(_.toSeq, _.toList)
  implicit def setCodec[A](implicit listCodec: Codec[List[A]]): Codec[Set[A]] =
    listCodec.xmap(_.toSet[A], _.toList)
  implicit def mapCodec[K, V](implicit listCodec: Codec[List[(K, V)]]): Codec[Map[K, V]] =
    listCodec.xmap(_.toMap[K, V], _.toList)
  implicit def datetimeCodec(implicit longCodec: Codec[Long]): Codec[DateTime] =
    longCodec.xmap(new DateTime(_), _.toInstant.getMillis)
  implicit def axisCodec(implicit strCodec: Codec[String]): Codec[Axis] =
    strCodec.xmap(
      str => {
        val Array(region, bracket) = str.split(',')
        Axis.parse(region, bracket).get
      },
      axis => s"${axis.region.slug},${axis.bracket.slug}")
}

import MyCodecImplicits._

val serialized = Codec.encode(state).require
Codec.decode[PersistedStorageState](serialized).require.value
serialized.size
val gzipped = Bijection.bytes2GzippedBytes(serialized.toByteArray)
gzipped.bytes.size

Storage.serializer(state).length
