package info.fotm.clustering

import info.fotm.domain.Domain._
import info.fotm.domain._
import info.fotm.util.Statistics.Metrics
import info.fotm.util.{Statistics, MathVector}

class ClusteringEvaluator(features: List[Feature[CharacterStatsUpdate]]) extends App {

  def findTeams(clusterer: RealClusterer, updates: Seq[CharacterStatsUpdate], teamSize: Int): Set[Team] =
    if (updates.isEmpty)
      Set()
    else {
      val featureVectors: Seq[MathVector] = Feature.normalize(features, updates)
      val featureMap = updates.map(_.id).zip(featureVectors).toMap
      val clusters = clusterer.clusterize(featureMap, teamSize)
      clusters.map(ps => Team(ps.toSet))
    }

  def evaluateStep(clusterer: RealClusterer,
                   ladder: CharacterLadder,
                   nextLadder: CharacterLadder,
                   games: Set[Game],
                   nLost: Int = 0): Statistics.Metrics = {
    print(".")
    val teamsPlayed: Set[Team] = games.flatMap(g => Seq(g._1, g._2))
    val currentSnapshots = teamsPlayed.map(t => (t, TeamSnapshot(t, ladder))).toMap
    val nextSnapshots = teamsPlayed.map(t => (t, TeamSnapshot(t, nextLadder))).toMap

    val (wTeams, leTeams) = teamsPlayed.partition(t => nextSnapshots(t).rating - currentSnapshots(t).rating > 0)
    val (eTeams, lTeams) = leTeams.partition(t => nextSnapshots(t).rating - currentSnapshots(t).rating == 0)

    // algo input: ladder diffs for playersPlayed
    val wDiffs = wTeams.flatMap(_.members).toList.map { p => CharacterStatsUpdate(p, ladder(p), nextLadder(p)) }
    val lDiffs = lTeams.flatMap(_.members).toList.map { p => CharacterStatsUpdate(p, ladder(p), nextLadder(p)) }
    val eDiffs = eTeams.flatMap(_.members).toList.map { p => CharacterStatsUpdate(p, ladder(p), nextLadder(p)) }

    // introduce "noise"
    val noisyWDiffs = wDiffs.drop(nLost / 2).dropRight(nLost / 2)
    val noisyLDiffs = lDiffs.drop(nLost / 2).dropRight(nLost / 2)

    // we can only correctly guess these
    val noiseFilteredIds = (noisyWDiffs ++ noisyLDiffs).map(_.id).toSet
    val noiseFilteredTeams = teamsPlayed.filter(_.members.forall(noiseFilteredIds.contains))

    // algo evaluation: match output against teamsPlayed
    val teamSize = ladder.axis.bracket.size

    val teams: Set[Team] =
      findTeams(clusterer, noisyWDiffs, teamSize) ++
        findTeams(clusterer, noisyLDiffs, teamSize) ++
        findTeams(clusterer, eDiffs, teamSize)

    // remove contentions (penalize multiplexer and merged algos?)
    val characters = teams.flatMap(t => t.members).toList
    val charTeams: Map[CharacterId, Set[Team]] = characters.map(c => (c, teams.filter(t => t.members.contains(c)))).toMap
    val (certain, _) = charTeams.partition(kv => kv._2.size == 1)

    val uncontended = certain.values.flatten.toSet
    Statistics.calcMetrics(uncontended, noiseFilteredTeams)
  }

  def evaluate(clusterer: RealClusterer, data: Seq[(CharacterLadder, CharacterLadder, Set[Game])]): Double = {
    val stats: Seq[Metrics] =
      for {
        (ladder, nextLadder, games) <- data
        noise = 2 * games.head._1.members.size - 1
      }
        yield evaluateStep(clusterer, ladder, nextLadder, games, noise)

    val combinedMetrics: Metrics = stats.reduce(_ + _)
    println(s"\n$combinedMetrics")

    Statistics.fScore(0.5)(combinedMetrics)
  }
}
