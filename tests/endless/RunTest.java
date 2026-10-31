package engine;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import screen.EndlessMode.Run;
import screen.EndlessMode.Records;
import screen.EndlessMode.Run.Body;
import screen.EndlessMode.Run.Phase;

/** Dependency-free regression checks for the removable endless add-on. */
public final class RunTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        countdownAndPause();
        scoringAndDifficulty();
        bossTriggersAndRewards();
        hitFeedback();
        collisionAndDeath();
        wavesAndReset();
        persistence();
        soak();

        System.out.println("PASS: " + checks + " endless regression checks");
    }

    private static Run fresh() { return new Run(432, 497, new Random(42)); }
    private static Run active() throws Exception {
        Run run = fresh();
        set(run, "phase", Phase.PLAYING);
        return run;
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    @SuppressWarnings("unchecked")
    private static List<Body> bodies(Run run, String name) throws Exception {
        Field field = Run.class.getDeclaredField(name);
        field.setAccessible(true);
        return (List<Body>) field.get(run);
    }
    private static void advance(Run run, int frames, int move, boolean fire) {
        for (int i = 0; i < frames; i++) run.update(1.0 / 60, move, fire);
    }
    private static void countdownAndPause() throws Exception {
        Run run = fresh();
        advance(run, 120, 1, true);
        check(run.getPhase() == Phase.READY && run.getSeconds() == 0, "Countdown is not survival");
        check(run.getShots().isEmpty(), "No firing in countdown");
        run.togglePause();
        int countdown = run.getCountdown();
        advance(run, 600, -1, true);
        check(run.getCountdown() == countdown, "Paused countdown frozen");
        run.togglePause();
        advance(run, 70, 0, false);
        check(run.getPhase() == Phase.PLAYING, "Countdown resumes");
        run.update(0.05, 1, true);
        double x = run.getPlayer().x, y = run.getShots().get(0).y;
        long score = run.getScore();
        run.togglePause();
        advance(run, 600, 1, true);
        check(run.getPlayer().x == x && run.getShots().get(0).y == y, "Paused actors frozen");
        check(run.getScore() == score, "Paused score frozen");
        run.togglePause();
        run.update(0.05, 1, false);
        check(run.getPlayer().x > x, "Resume works");
    }
    private static void scoringAndDifficulty() throws Exception {
        Run run = active();
        set(run, "elapsed", 120.0);
        set(run, "kills", 20);
        check(run.getScore() == 3200, "KFC scoring example");
        check(run.getDifficulty() == 1.20, "5 percent per 30 seconds");
        run.update(Double.NaN, 0, false);
        run.update(-1, 0, false);
        check(run.getSeconds() == 120, "Invalid deltas ignored");
        run.update(60, 1, false);
        check(run.getSeconds() == 120, "Lag does not award idle survival time");
        advance(run, 200, -1, false);
        check(run.getPlayer().x >= 8, "Left boundary");
        advance(run, 300, 1, false);
        check(run.getPlayer().x + 26 <= 424, "Right boundary");
    }
    private static void bossTriggersAndRewards() throws Exception {
        Run run = active();
        set(run,"elapsed",120.0);
        set(run,"kills",30);
        run.update(0.02, 0, false);
        check(run.getBoss()==null && run.getNextBossWave()==5,"Time and kill counts no longer trigger a mid-wave boss");
        run = active();
        set(run,"wave",4);bodies(run,"enemies").clear();run.update(.001,0,false);
        check(run.getWave()==5 && run.getPhase()==Phase.BOSS_INTRO,"Wave 5 begins with a boss introduction");
        check(run.getEnemies().size()==1 && run.getBossDamage()==1,"Boss wave has only the boss and truthful damage stats");
        Body boss = run.getBoss();
        double bossX=boss.x,playerX=run.getPlayer().x;
        long seconds=run.getSeconds(),score=run.getScore();
        advance(run,60,1,true);
        check(run.getPhase()==Phase.BOSS_INTRO && boss.x==bossX && run.getPlayer().x==playerX,"Opening freezes boss and player movement");
        check(run.getShots().isEmpty() && run.getSeconds()==seconds && run.getScore()==score,"Opening has no shots or survival points");
        run.togglePause();int countdown=run.getCountdown();advance(run,120,1,true);
        check(run.getCountdown()==countdown,"Pausing freezes boss introduction");
        run.togglePause();advance(run,130,0,false);
        check(run.getPhase()==Phase.PLAYING,"Opening automatically starts the duel after three seconds");
        int livesBefore=run.getLives();
        set(run,"enemyShotClock",0.0);set(run,"invulnerability",0.0);run.update(.001,0,false);
        java.util.function.Predicate<Body> playerShot=run::isPlayerShot;
        Body incoming=run.getShots().stream().filter(playerShot.negate()).findFirst().get();
        incoming.x=run.getPlayer().x+4;incoming.y=run.getPlayer().y+2;run.update(.001,0,false);
        check(livesBefore-run.getLives()==run.getBossDamage(),"Boss projectile damage matches the introduction's stated life loss");
        run.update(0.01, 0, true);
        Body shot = run.getShots().stream().filter(run::isPlayerShot).findFirst().get();
        shot.x = boss.x + 20;
        shot.y = boss.y + 15;
        boss.hp = 1;
        run.update(0.001, 0, false);
        check(run.getBoss() == null && run.getBosses() == 1, "Boss defeated once");
        check(run.getWave()==6 && run.getNextBossWave()==10,"Boss defeat advances to wave 6; next boss is wave 10");
        check(run.getDamageUpgrades() + run.getSpeedUpgrades() + run.getProjectiles() - 1 == 0,
                "Boss grants no automatic upgrade");
        check(run.getShots().isEmpty() && run.getPhase() == Phase.INTERMISSION, "Safe reward break");
        check(run.getKills() == 1, "Boss counts as one kill");
        advance(run, 130, 0, false);
        check(run.getBosses() == 1 && run.getBoss() == null, "Reward cannot repeat");
    }
    private static void hitFeedback() throws Exception {
        Run run=active();Body target=run.getEnemies().get(0);target.hp=3;
        set(run,"damage",1.5);
        java.util.List<Double> hits=new java.util.ArrayList<Double>();
        set(run,"enemyHit",(java.util.function.BiConsumer<Body,Double>)(enemy,amount)->hits.add(amount));
        shootAt(run,target);
        check(target.hp==1.5 && run.getKills()==0,"Nonlethal hits apply upgraded damage without awarding a kill");
        check(hits.size()==1 && hits.get(0)==1.5,"Nonlethal hit emits its damage for visual feedback");
        set(run,"shotClock",0.0);run.update(.001,0,true);
        Body miss=run.getShots().stream().filter(run::isPlayerShot).findFirst().get();
        miss.x=-20;run.update(.001,0,false);
        check(hits.size()==1,"Missed projectiles emit no hit feedback");
        shootAt(run,target);
        check(hits.size()==2 && run.getKills()==1,"Lethal hit emits one feedback event and credits one kill");
    }
    private static void shootAt(Run run,Body target)throws Exception {
        set(run,"shotClock",0.0);run.update(.001,0,true);
        Body shot=run.getShots().stream().filter(run::isPlayerShot).findFirst().get();
        shot.x=target.x+4;shot.y=target.y+4;run.update(.001,0,false);
    }
    private static void collisionAndDeath() throws Exception {
        Run run = active();
        Body target = run.getEnemies().get(0);
        Body second = run.getEnemies().get(1);
        second.x = target.x;
        second.y = target.y;
        run.update(0.001, 0, true);
        Body shot = run.getShots().get(0);
        shot.x = target.x;
        shot.y = target.y + 4;
        run.update(0.001, 0, false);
        check(run.getKills() == 1, "One bullet never kills two overlapping targets");
        run = active();
        set(run, "enemyShotClock", 0.0);
        run.update(0.001, 0, false);
        shot = run.getShots().get(0);
        shot.x = run.getPlayer().x;
        shot.y = run.getPlayer().y;
        set(run, "invulnerability", 0.0);
        run.update(0.001, 0, false);
        check(run.getLives() == 2 && run.isInvulnerable(), "Hit grants protection");
        set(run, "enemyShotClock", 0.0);
        run.update(0.001, 0, false);
        shot = run.getShots().get(0);
        shot.x = run.getPlayer().x;
        shot.y = run.getPlayer().y;
        run.update(0.001, 0, false);
        check(run.getLives() == 2, "Protection prevents chained hits");
        set(run, "lives", 1);
        set(run, "invulnerability", 0.0);
        set(run, "enemyShotClock", 0.0);
        run.update(0.001, 0, false);
        shot = run.getShots().get(0);
        shot.x = run.getPlayer().x;
        shot.y = run.getPlayer().y;
        run.update(0.001, 0, false);
        check(run.getPhase() == Phase.OVER && run.getLives() == 0, "Last hit ends run");
        long score = run.getScore();
        advance(run, 500, 0, true);
        check(run.getScore() == score, "No scoring after death");
        run = active();
        run.getEnemies().get(0).y = run.getPlayer().y;
        run.update(0.001, 0, false);
        check(run.getPhase() == Phase.OVER, "Formation breach ends run");
    }
    private static void wavesAndReset() throws Exception {
        Run run = active();
        set(run, "wave", 10);
        bodies(run, "enemies").clear();
        run.update(0.001, 0, false);
        check(run.getWave() == 11 && run.getPhase() == Phase.INTERMISSION, "Continues beyond campaign");
        check(run.getEnemies().size() <= 35, "Formation bounded");
        run = active();
        set(run, "wave", 3);
        set(run, "lives", 2);
        bodies(run, "enemies").clear();
        run.update(0.001, 0, false);
        check(run.getLives() == 3, "Repair after three cleared waves");
        run.finish("RUN ENDED");
        run = fresh();
        check(run.getWave() == 1 && run.getScore() == 0 && run.getLives() == 3
                && run.getProjectiles() == 1 && run.getBosses() == 0, "Retry starts clean");
    }
    private static void persistence() throws Exception {
        Path directory = Files.createTempDirectory("endless-tests-");
        Path file = directory.resolve("records");
        try {
            Records records = new Records(file);
            check(records.getScore() == 0, "Missing file defaults");
            Run run = active();
            set(run, "elapsed", 120.0);
            set(run, "kills", 20);
            check(records.save(run), "Record saved");
            records = new Records(file);
            check(records.getScore() == 3200 && records.getSeconds() == 120 && records.getKills() == 20,
                    "Records survive restart");
            records.save(fresh());
            check(new Records(file).getScore() == 3200, "Lower run cannot replace best");
            Files.write(file, "score=broken".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            check(new Records(file).getScore() == 0, "Corrupt file does not crash");
            check(!new Records(directory.resolve("absent/records")).save(run), "Save failure handled");
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(directory); }
    }
    private static void soak() throws Exception {
        Run run = active();
        for (int i = 0; i < 60000; i++) {
            if (run.getPhase() == Phase.OVER) run = active();
            if (run.getPhase() == Phase.REWARD) run.chooseReward(0);
            run.update(1.0 / 60, (i / 90) % 2 == 0 ? 1 : -1, true);
            if (run.getEnemies().size() > 36 || run.getShots().size() > 200)
                throw new AssertionError("Unbounded actor growth");
        }
        check(true, "60,000 frame soak, no unbounded actors or exceptions");
    }

}
