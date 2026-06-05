class Damageable {

    int hp;
    int maxHp;

    private long lastHitTime = 0;
    private long stunEndTime = 0;

    private static final int INVULN_MS = 500;
    private static final int STUN_MS = 120;
    private static final int FLASH_MS = 120;

    Damageable(int hp) {
        this.hp = hp;
        this.maxHp = hp;
    }

    boolean canTakeDamage() {
        return System.currentTimeMillis() - lastHitTime > INVULN_MS;
    }

    void takeDamage(int amount) {
        if (!canTakeDamage()) return;
        long now = System.currentTimeMillis();
        hp -= amount;
        lastHitTime = now;
        stunEndTime = now + STUN_MS;
    }

    boolean isStunned() {
        return System.currentTimeMillis() < stunEndTime;
    }

    boolean isFlashing() {
        return System.currentTimeMillis() - lastHitTime < FLASH_MS;
    }

    boolean alive() {
        return hp > 0;
    }
}
