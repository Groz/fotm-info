package info.fotm.aether

import akka.actor.{Actor, ActorIdentity, ActorRef, Props}
import akka.event.{Logging, LoggingReceive}
import com.github.nscala_time.time.Imports._
import com.twitter.bijection.{Bijection, GZippedBytes}
import info.fotm.domain._
import info.fotm.util._
import scodec.Codec
import scodec.bits.BitVector
import scodec.codecs.implicits._

final case class FotmSetup(specIds: Set[Int], ratio: Double)

object Storage {
  val props: Props = Props[Storage]
  val readonlyProps: Props = Props(classOf[Storage], true)

  val identifyMsgId = "storage"
  val Identify = akka.actor.Identify(identifyMsgId)

  // input
  final case class Updates(axis: Axis, teamUpdates: Seq[TeamUpdate], charUpdates: Set[CharacterDiff]) {
    override val toString = s"Updates($axis, teams: ${teamUpdates.size}, chars: ${charUpdates.size})"
  }

  final case class QueryFotm(axis: Axis, interval: Interval)
  final case class QueryFotmResponse(axis: Axis, setups: Seq[FotmSetup])

  // output
  final case class QueryState(axis: Axis, interval: Interval)
  final case class QueryStateResponse(axis: Axis, teams: Seq[TeamSnapshot], chars: Seq[CharacterSnapshot])

  // reactive, subscribes/unsubscribes sender to updates
  case object Subscribe

  case object Unsubscribe

  // I'm online (again?)!
  case object Announce

  val keyPathBijection =
    Bijection.build[Axis, String] { axis =>
      s"${axis.region.slug}/${axis.bracket.slug}"
    } { str =>
      val Array(r, b) = str.split('/')
      Axis.parse(r, b).get
    }

  import info.fotm.MyCodecImplicits._

  def scodecGzipBijection[T](implicit codec: Codec[T]): Bijection[T, Array[Byte]] = Bijection.build[T, Array[Byte]] { t =>
    val bytes = Codec.encode(t).require.toByteArray
    Bijection.bytes2GzippedBytes(bytes).bytes
  } { gzippedBytes =>
    val bytes = Bijection.bytes2GzippedBytes.inverse(GZippedBytes(gzippedBytes))
    Codec.decode[T](BitVector(bytes)).require.value
  }

  lazy val fromConfig =
    AetherConfig.storagePersistence[Axis, StorageAxis](keyPathBijection, scodecGzipBijection[StorageAxis])
}

class Storage(persistence: Persisted[Map[Axis, StorageAxis]]) extends Actor {

  import Storage._

  def this(proxy: Boolean) = this(if (proxy) Storage.fromConfig.readonly else Storage.fromConfig)

  def this() = this(false)

  val log = Logging(context.system, this.getClass)

  override def receive: Receive = {
    val init = Axis.all.map { (_, StorageAxis()) }.toMap

    val state = init ++ persistence.fetch().fold(Map.empty[Axis, StorageAxis])(identity)

    process(state, Set.empty)
  }

  def process(state: Map[Axis, StorageAxis], subs: Set[ActorRef]): Receive = LoggingReceive {

    case msg@Updates(axis, teamUpdates: Seq[TeamUpdate], charUpdates: Set[CharacterDiff]) =>
      log.debug("Updates received. Processing...")
      val storageAxis = state(axis)

      val updatedState = storageAxis.update(teamUpdates, charUpdates.map(_.current))
      persistence.save(Map(axis -> updatedState)) // save only changed axis

      context.become(process(state.updated(axis, updatedState), subs))

      for (sub <- subs)
        sub ! msg

    case QueryState(axis: Axis, unadjustedInterval: Interval) =>
      val interval = new Interval(unadjustedInterval.start, unadjustedInterval.end + 100.millis)
      val storageAxis = state(axis)
      val teams: Set[TeamSnapshot] = storageAxis.teams(interval)
      val chars: Map[CharacterId, CharacterSnapshot] =
        storageAxis.chars(interval).map(c => (c.id, c))(scala.collection.breakOut)

      // filter out chars seen in teams that are sent back
      val charsInTeams: Set[CharacterId] = teams.flatMap(_.team.members)
      val charsNotInTeams = (chars -- charsInTeams).values.toSeq

      sender ! QueryStateResponse(axis, teams.toSeq.sortBy(-_.rating), charsNotInTeams.toSeq.sortBy(-_.stats.rating))

    case QueryFotm(axis: Axis, interval: Interval) =>
      ???

    case Subscribe =>
      context.become(process(state, subs + sender))

    case Unsubscribe =>
      context.become(process(state, subs - sender))

    case Announce =>
      sender ! Subscribe

    case ActorIdentity(correlationId, storageRefOpt) if correlationId == identifyMsgId =>
      log.debug(s"Storage actor identity: $storageRefOpt")
      storageRefOpt.foreach { storageRef => storageRef ! Subscribe }
  }
}
