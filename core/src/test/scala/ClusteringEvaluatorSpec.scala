import info.fotm.clustering._
import ClusteringEvaluator._
import info.fotm.domain.Domain.LadderSnapshot
import info.fotm.domain.Team
import org.scalatest._

class ClusteringEvaluatorSpec extends FlatSpec with Matchers {
  val players1500 = (1 to 3).map(i => genPlayer)
  val players1580 = (1 to 3).map(i => genPlayer.copy(rating = 1580))
  val player1500 = players1500.head
  val player1580 = players1580.head
  val team1500 = Team(players1500.map(_.id).toSet)
  val team1580 = Team(players1580.map(_.id).toSet)

  val ladder: LadderSnapshot = (players1500 ++ players1580).map(p => (p.id, p)).toMap

  "Team rating" should "be mean of players' ratings" in {
    team1580.rating(ladder) should be(1580)
  }

  it should "be mean of players' ratings 2" in {
    team1500.rating(ladder) should be(1500)
  }

  it should "be mean of players' ratings 3" in {
    Team(Seq(player1500, player1580).map(_.id).toSet).rating(ladder) should be (1540)
  }

  //def calcRatingChange(winnerRating: Double, loserRating: Double): Int = {
  "calcRatingChange" should "output 16 for equal teams" in {
    calcRatingChange(1600, 1600) should be (16)
  }

  it should "output 20 for 1500 and 1580" in {
    calcRatingChange(1500, 1580) should be (20)
  }

  it should "output 12 for 1580 and 1500" in {
    calcRatingChange(1580, 1500) should be (12)
  }

  //def calcRating(charInfo: CharacterInfo, team: Team, won: Boolean): Int = {
  "calcRating" should "increase rating by 16 if player wins over equal team" in {
    calcRating(player1500.id, team1500, won = true)(ladder) should be(1516)
  }

  it should  "decrease rating by 16 if player loses to equal team" in {
    calcRating(player1500.id, team1500, won = false)(ladder) should be (1484)
  }

  it should "increase rating by 20 if 1500 player wins over 1580 team" in {
    calcRating(player1500.id, team1580, won = true)(ladder) should be(1520)
  }

  it should "decrease rating by 12 if 1500 player loses to 1580 team" in {
    calcRating(player1500.id, team1580, won = false)(ladder) should be(1488)
  }

  it should "increase rating by 12 if 1580 player wins over 1500 team" in {
    calcRating(player1580.id, team1500, won = true)(ladder) should be(1592)
  }

  it should "decrease rating by 20 if 1580 player loses to 1500 team" in {
    calcRating(player1580.id, team1500, won = false)(ladder) should be(1560)
  }

  "play" should "change ratings for all players accordingly" in {
    val nextLadder = play(ladder, team1580, team1500)
    team1500.members.map(nextLadder).foreach { _.rating should be(1488) } // zieg
    team1580.members.map(nextLadder).foreach { _.rating should be(1592) }
  }

  "prepare data" should "return correct first ladder" in {
    val firstMatches = (team1580, team1500)
    val data: Stream[(LadderSnapshot, LadderSnapshot, Set[(Team, Team)])] =
      prepareData(Some(ladder), Some(Seq(team1500, team1580)), identity, (_, _) => Seq(firstMatches))

    val (prevLadder, currentLadder, matchesPlayed) = data.head
    prevLadder should contain theSameElementsAs ladder

    team1500.members.map(currentLadder).foreach { _.rating should be(1488) }
    team1580.members.map(currentLadder).foreach { _.rating should be(1592) }
    matchesPlayed should contain theSameElementsAs Seq(firstMatches)
  }

  it should "make correct transition to second ladder" in {
    val matches = (team1580, team1500)
    val data: Stream[(LadderSnapshot, LadderSnapshot, Set[(Team, Team)])] =
      prepareData(Some(ladder), Some(Seq(team1500, team1580)), identity, (_, _) => Seq(matches))

    val (initLadder, firstLadder, firstMatchesPlayed) = data.head
    val (prevLadder, currentLadder, secondMatchesPlayed) = data.tail.head

    prevLadder should contain theSameElementsAs firstLadder

    team1500.members.map(currentLadder).foreach { _.rating should be(1477) }
    team1580.members.map(currentLadder).foreach { _.rating should be(1603) }
    secondMatchesPlayed should contain theSameElementsAs Seq(matches)
  }

  "replace player" should "remove one player and add another" in {
    val rt = replacePlayer(team1500, player1500.id, player1580.id)
    rt.members should contain (player1580.id)
    rt.members shouldNot contain (player1500.id)
  }

  "hop teams" should "exhange players between 1500 and 1580 teams" in {
    val (t1, t2) = hopTeams(team1500, team1580)
    t1.members.intersect(team1580.members).size should be(1)
    t2.members.intersect(team1500.members).size should be(1)
  }

  "hopTeamsRandomly" should "preserve the number of teams" in {
    hopTeamsRandomly(Seq(team1500, team1580)).size should be(2)
    // TODO: expand this test
  }
}
