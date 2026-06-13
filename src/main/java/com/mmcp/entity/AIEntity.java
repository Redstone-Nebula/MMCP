package com.mmcp.entity;

import com.mmcp.ai.AIBrain;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AIEntity extends PathfinderMob {
    private final AIBrain aiBrain;
    @Nullable
    private Player followingPlayer;
    private int actionCooldown = 0;
    private int independentActionInterval = 0;

    public AIEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.aiBrain = new AIBrain(this);
    }

    @Override
    protected void registerGoals() {
        // 优先级从高到低
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 12.0F, 1.0F));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 0.8D, 60));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.4F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6D));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false; // 不会自然消失
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            tickServer();
        }
    }

    private void tickServer() {
        if (actionCooldown > 0) {
            actionCooldown--;
        }

        // AI大脑决策周期：每 40 tick (~2秒) 评估一次
        if (this.tickCount % 40 == 0) {
            aiBrain.update();
        }

        // 跟随玩家逻辑
        if (followingPlayer != null) {
            if (!followingPlayer.isAlive() || this.distanceToSqr(followingPlayer) > 64.0D * 64.0D) {
                stopFollowing();
                return;
            }
            if (this.distanceToSqr(followingPlayer) > 4.0D) {
                this.getNavigation().moveTo(followingPlayer, 0.85D);
            } else {
                this.getNavigation().stop();
                // 靠近玩家时看向玩家
                this.lookAt(followingPlayer, 30.0F, 30.0F);
            }
        }

        // 独立自主行为间隔
        if (independentActionInterval > 0) {
            independentActionInterval--;
        } else {
            performIndependentAction();
            independentActionInterval = 100 + this.random.nextInt(200); // 5~15秒间隔
        }
    }

    /**
     * 自主行为：随机做各种动作
     */
    private void performIndependentAction() {
        if (followingPlayer != null) return; // 跟随模式下不做自主动作

        double rand = this.random.nextDouble();

        if (rand < 0.4D) {
            // 随机漫步
            // 已有 RandomStrollGoal 处理
        } else if (rand < 0.6D) {
            // 四处张望
            this.setYRot(this.yRotO + this.random.nextFloat() * 60.0F - 30.0F);
        } else if (rand < 0.75D) {
            // 看向最近的玩家
            Player nearest = this.level().getNearestPlayer(this, 16.0D);
            if (nearest != null) {
                this.lookAt(nearest, 30.0F, 30.0F);
            }
        } else if (rand < 0.85D) {
            // 跳跃
            if (this.onGround()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0, 0.5D, 0));
            }
        } else {
            // 随机发出声音（无声息的粒子效果替代）
            if (this.level() instanceof ServerLevel serverLevel) {
                double x = this.getX() + (this.random.nextDouble() - 0.5D) * 1.5D;
                double y = this.getY() + 2.0D;
                double z = this.getZ() + (this.random.nextDouble() - 0.5D) * 1.5D;
                serverLevel.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.NOTE,
                        x, y, z, 1, 0, 0, 0, 0
                );
            }
        }
    }

    // ==================== 公开控制方法 ====================

    public void followPlayer(Player player) {
        this.followingPlayer = player;
        this.actionCooldown = 0;
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("§b[MMCP AI]§f 正在跟随 " + player.getName().getString()), false
            );
        }
    }

    public void stopFollowing() {
        if (this.followingPlayer != null && this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("§b[MMCP AI]§f 停止跟随 " + this.followingPlayer.getName().getString()), false
            );
        }
        this.followingPlayer = null;
        this.getNavigation().stop();
    }

    public void moveToPosition(double x, double y, double z) {
        this.followingPlayer = null;
        this.getNavigation().moveTo(x, y, z, 0.8D);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("§b[MMCP AI]§f 正在前往 (" + String.format("%.1f", x) + ", " +
                            String.format("%.1f", y) + ", " + String.format("%.1f", z) + ")"), false
            );
        }
    }

    public void broadcastChat(String message) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("§b[MMCP AI]§f " + message), false
            );
        }
    }

    // ==================== 查询方法 ====================

    public double getHealthPercent() {
        return getHealth() / getMaxHealth();
    }

    @Nullable
    public Player getNearestPlayerInRange(double range) {
        return this.level().getNearestPlayer(this, range);
    }

    public List<Player> getNearbyPlayers(double range) {
        return this.level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(range));
    }

    public boolean isFollowingPlayer() {
        return followingPlayer != null;
    }

    @Nullable
    public Player getFollowingPlayer() {
        return followingPlayer;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // 减少伤害
        if (!this.level().isClientSide && source.getEntity() instanceof Player player) {
            // 右键交互处理在 InteractionGoal 中完成
        }
        return super.hurt(source, Math.min(amount, 6.0F)); // 单次伤害上限
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}