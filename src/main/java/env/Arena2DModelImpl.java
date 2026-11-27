package env;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class Arena2DModelImpl implements Arena2DModel {

    private static class Pose {
        private final Vector2D position;
        private final Orientation orientation;

        public Pose(Vector2D position, Orientation orientation) {
            this.position = Objects.requireNonNull(position);
            this.orientation = Objects.requireNonNull(orientation);
        }

        public Vector2D getPosition() {
            return position;
        }

        public Orientation getOrientation() {
            return orientation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pose pose = (Pose) o;
            return position.equals(pose.position) && orientation == pose.orientation;
        }

        @Override
        public int hashCode() {
            return Objects.hash(position, orientation);
        }

        @Override
        public String toString() {
            return "Pose{" + "position=" + position + ", direction=" + orientation + '}';
        }
    }

    private final Map<String, Pose> agentPoses = Collections.synchronizedMap(new HashMap<>());
    private final int width;
    private final int height;
    private final BiFunction<Vector2D, Vector2D, Boolean> neighbourhoodFunction;
    private int actionDelayMillis = 500;
    private double slideProbability = 0d;

    public Arena2DModelImpl(int width, int height) {
        this(width, height, (a, b) -> {
            Vector2D distanceVector = b.minus(a);
            return Math.abs(distanceVector.getX()) <= 1 && Math.abs(distanceVector.getY()) <= 1;
        });
    }

    public Arena2DModelImpl(int width, int height, BiFunction<Vector2D, Vector2D, Boolean> neighbourhoodFunction) {
        this.width = width;
        this.height = height;
        this.neighbourhoodFunction = Objects.requireNonNull(neighbourhoodFunction);
    }

    @Override
    public boolean containsAgent(String name) {
        return agentPoses.containsKey(name);
    }

    @Override
    public Set<String> getAllAgents() {
        return agentPoses.keySet();
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public boolean isPositionInside(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private void ensureAgentExists(String agent) {
        if (!containsAgent(agent)) {
            throw new IllegalArgumentException("No such an agent: " + agent);
        }
    }

    @Override
    public Vector2D getAgentPosition(String agent) {
        synchronized (agentPoses) {
            ensureAgentExists(agent);
            return agentPoses.get(agent).getPosition();
        }
    }
}