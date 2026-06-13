package com.mmcp.ai;

import com.mmcp.entity.AIEntity;
import net.minecraft.world.entity.player.Player;

/**
 * AI大脑决策系统
 * 控制AI实体的自主决策和行为选择
 */
public class AIBrain {
    private final AIEntity entity;

    // 决策权重
    private static final float WEIGHT_FOLLOW_PLAYER = 0.3f;
    private static final float WEIGHT_WANDER = 0.4f;
    private static final float WEIGHT_SOCIALIZE = 0.15f;
    private static final float WEIGHT_IDLE = 0.15f;

    // 状态
    private AIState currentState = AIState.IDLE;
    private int stateTimer = 0;
    private Player targetPlayer = null;

    public enum AIState {
        IDLE,
        WANDERING,
        FOLLOWING_PLAYER,
        SOCIALIZING
    }

    public AIBrain(AIEntity entity) {
        this.entity = entity;
    }

    /**
     * 每 tick 调用的大脑更新
     * 实际调用频率由 AIEntity 控制（每 40 tick 一次）
     */
    public void update() {
        stateTimer++;

        // 根据当前状态执行
        switch (currentState) {
            case FOLLOWING_PLAYER:
                updateFollowingState();
                break;
            case WANDERING:
                updateWanderingState();
                break;
            case SOCIALIZING:
                updateSocializingState();
                break;
            default:
                break;
        }

        // 定期重新决策
        if (stateTimer > 60) { // 约3秒后重新评估
            evaluateNewState();
            stateTimer = 0;
        }
    }

    private void evaluateNewState() {
        // 如果已经有跟随目标，保持
        if (entity.isFollowingPlayer()) return;

        double roll = entity.getRandom().nextDouble();

        if (roll < WEIGHT_FOLLOW_PLAYER) {
            // 尝试跟随最近的玩家
            Player nearest = entity.getNearestPlayerInRange(16.0D);
            if (nearest != null) {
                currentState = AIState.FOLLOWING_PLAYER;
                targetPlayer = nearest;
                entity.followPlayer(nearest);
                return;
            }
        }

        if (roll < WEIGHT_FOLLOW_PLAYER + WEIGHT_WANDER) {
            currentState = AIState.WANDERING;
            return;
        }

        if (roll < WEIGHT_FOLLOW_PLAYER + WEIGHT_WANDER + WEIGHT_SOCIALIZE) {
            Player nearest = entity.getNearestPlayerInRange(8.0D);
            if (nearest != null) {
                currentState = AIState.SOCIALIZING;
                targetPlayer = nearest;
                // 看向玩家并发送问候
                entity.lookAt(nearest, 30.0F, 30.0F);
                trySocialize();
                return;
            }
        }

        currentState = AIState.IDLE;
    }

    private void updateFollowingState() {
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            entity.stopFollowing();
            currentState = AIState.IDLE;
            targetPlayer = null;
            return;
        }

        // 持续跟随
        entity.followPlayer(targetPlayer);

        // 偶尔互动
        if (entity.getRandom().nextFloat() < 0.02f) {
            trySocialize();
        }
    }

    private void updateWanderingState() {
        // RandomStrollGoal 会自动处理漫步
        // 这里可以添加额外的探索行为
        if (entity.getRandom().nextFloat() < 0.05f) {
            // 偶尔看向周围
            entity.setYRot(entity.getYRot() + entity.getRandom().nextFloat() * 120.0F - 60.0F);
        }
    }

    private void updateSocializingState() {
        if (targetPlayer == null || !targetPlayer.isAlive() ||
                entity.distanceToSqr(targetPlayer) > 12.0D * 12.0D) {
            currentState = AIState.IDLE;
            targetPlayer = null;
            return;
        }

        entity.lookAt(targetPlayer, 30.0F, 30.0F);

        // 社交互动 - 发送粒子效果
        if (entity.tickCount % 20 == 0) {
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.HEART,
                        entity.getX(), entity.getY() + 2.0D, entity.getZ(),
                        1, 0.3D, 0.3D, 0.3D, 0
                );
            }
        }
    }

    private void trySocialize() {
        if (targetPlayer == null) return;

        String[] greetings = {
                "你好!", "Hello!", "嗨～",
                "有什么需要帮忙的吗?", "很高兴见到你!"
        };
        String msg = greetings[entity.getRandom().nextInt(greetings.length)];
        entity.broadcastChat(msg);

        // 发送爱心粒子
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.HEART,
                    entity.getX(), entity.getY() + 2.0D, entity.getZ(),
                    3, 0.5D, 0.5D, 0.5D, 0
            );
        }
    }

    public void onPlayerInteract(Player player) {
        targetPlayer = player;
        currentState = AIState.SOCIALIZING;
        stateTimer = 0;
        trySocialize();
    }

    public AIState getCurrentState() {
        return currentState;
    }
}