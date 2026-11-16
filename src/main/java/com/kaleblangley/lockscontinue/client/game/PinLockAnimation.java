package com.kaleblangley.lockscontinue.client.game;

/** Client-only motion state; never changes the authoritative pin puzzle. */
final class PinLockAnimation {
    private final float horizontalResponse;
    private final float inputNudge;
    private final double bounceDuration;
    private final double transitionDuration;
    private final float[] pinPositions;
    private final float[] transitionFrom;
    private final double[] transitionStarts;
    private final double[] transitionDurations;
    private float previousPickX;
    private float pickX;
    private float targetPickX;
    private float renderedPickX;
    private boolean targetChanged;
    private double renderTime;
    private double bounceStart;

    PinLockAnimation(int pinCount, float initialPickX, float horizontalResponse,
                     float inputNudge, double bounceDuration, double transitionDuration) {
        this.horizontalResponse = horizontalResponse;
        this.inputNudge = inputNudge;
        this.bounceDuration = bounceDuration;
        this.transitionDuration = transitionDuration;
        pinPositions = new float[pinCount];
        transitionFrom = new float[pinCount];
        transitionStarts = new double[pinCount];
        transitionDurations = new double[pinCount];
        previousPickX = pickX = targetPickX = renderedPickX = initialPickX;
        bounceStart = -bounceDuration;
    }

    void movePick(float delta, float minX, float maxX) {
        float target = Math.max(minX, Math.min(maxX, targetPickX + delta));
        targetChanged |= target != targetPickX;
        targetPickX = target;
    }

    void tick() {
        // All position changes happen after taking the previous-tick snapshot.
        previousPickX = pickX;
        float response = targetChanged
                ? 1.0F - (1.0F - inputNudge) * (1.0F - horizontalResponse)
                : horizontalResponse;
        pickX += (targetPickX - pickX) * response;
        if (Math.abs(targetPickX - pickX) < 0.001F) {
            pickX = targetPickX;
        }
        targetChanged = false;
    }

    void beginFrame(double tickTime) {
        renderTime = Math.max(renderTime, tickTime);
    }

    double renderTime() {
        return renderTime;
    }

    float pickPosition(float partialTick) {
        float alpha = Math.max(0.0F, Math.min(1.0F, partialTick));
        renderedPickX = previousPickX + (pickX - previousPickX) * alpha;
        return renderedPickX;
    }

    float renderedPickX() {
        return renderedPickX;
    }

    void startBounce() {
        bounceStart = renderTime;
    }

    float bouncePosition() {
        double elapsed = renderTime - bounceStart;
        if (elapsed <= 0.0D || elapsed >= bounceDuration) {
            return 0.0F;
        }
        double sine = Math.sin(Math.PI * elapsed / bounceDuration);
        return (float) (sine * sine);
    }

    void transitionPins() {
        for (int index = 0; index < pinPositions.length; index++) {
            transitionPin(index, transitionDuration);
        }
    }

    void transitionPin(int index, double duration) {
        transitionFrom[index] = pinPositions[index];
        transitionStarts[index] = renderTime;
        transitionDurations[index] = Math.max(0.0D, duration);
    }

    float pinPosition(int index, float puzzlePosition) {
        float target = Math.max(0.0F, Math.min(1.0F, puzzlePosition));
        double duration = transitionDurations[index];
        double elapsed = renderTime - transitionStarts[index];
        if (duration > 0.0D && elapsed < duration) {
            double t = Math.max(0.0D, elapsed / duration);
            float blend = (float) (t * t * (3.0D - 2.0D * t));
            target = transitionFrom[index] + (target - transitionFrom[index]) * blend;
        } else {
            transitionDurations[index] = 0.0D;
        }
        pinPositions[index] = target;
        return target;
    }

    float renderedPinPosition(int index) {
        return pinPositions[index];
    }
}
