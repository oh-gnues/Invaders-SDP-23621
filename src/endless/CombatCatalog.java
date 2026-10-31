package endless;

import engine.*;

import entity.Boss;
import entity.FinalBoss;
import entity.MidBoss;
import entity.EnemyShip;

/** Endless-owned selection of existing native entities. */
public final class CombatCatalog {
    private CombatCatalog() { }

    public static EnemyShip enemyForWave(int wave, int index, int x, int y) {
        java.util.List<DrawManager.SpriteType> types = new java.util.ArrayList<DrawManager.SpriteType>();
        for (DrawManager.SpriteType type : DrawManager.SpriteType.values())
            if (type.name().startsWith("EnemyShip") && type.name().endsWith("1")) types.add(type);
        return new EnemyShip(x, y, types.get(Math.floorMod(wave + index / 7, types.size())));
    }

    public static Boss bossForStage(int stage, int centerX, int y) {
        return stage % Core.getNumLevels() == 0
            ? new FinalBoss(centerX - 24, y, DrawManager.SpriteType.FinalBoss1)
            : new MidBoss(centerX - 16, y, DrawManager.SpriteType.MidBoss1);
    }
}
