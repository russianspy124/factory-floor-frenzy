class Damageable {
    int hp;
    long lastHit = 0;
    int invuln = 500;
    int stunDuration = 120;
    long stunEnd = 0;
    final int flashDuration=120;
    Damageable(int hp) { this.hp = hp; }
    
    boolean canTakeDamage() {
        return System.currentTimeMillis() - lastHit > invuln;
    }
    void takeDamage(int dmg) {
        long now = System.currentTimeMillis();
        if (!canTakeDamage()) return;
        hp -= dmg;
        lastHit = now;
        stunEnd = now + stunDuration;
    }
    boolean isStunned() {
        return System.currentTimeMillis() < stunEnd;
    }
    boolean isFlashing() {
        return System.currentTimeMillis() - lastHit < flashDuration;
    }
    boolean alive() { return hp > 0; }
}