package env;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface Arena2DModel {
    boolean containsAgent(String name);
    Set<String> getAllAgents();
    int getWidth();
    int getHeight();
    boolean isPositionInside(int x, int y);
    default boolean isPositionInside(Vector2D position) {
        return isPositionInside(position.getX(), position.getY());
    }
    default boolean isPositionOutside(int x, int y) {
        return !isPositionInside(x, y);
    }
    default boolean isPositionOutside(Vector2D position) {
        return isPositionOutside(position.getX(), position.getY());
    }
    Vector2D getAgentPosition(String agent);
    Optional<String> getAgentByPosition(Vector2D position);
    Orientation getAgentDirection(String agent);
    boolean setAgentPose(String agent, int x, int y, Orientation orientation);
    default boolean setAgentPose(String agent, Vector2D position, Orientation orientation) {
        return setAgentPose(agent, position.getX(), position.getY(), orientation);
    }
    default boolean setAgentPoseRandomly(String agent) {
        return setAgentPose(agent, Vector2D.random(getWidth(), getHeight()), Orientation.random());
    }
    default boolean moveAgent(String agent, int stepSize, Direction direction) {
        final Orientation newOrientation = getAgentDirection(agent).rotate(direction);
        return setAgentPose(agent, getAgentPosition(agent).afterStep(stepSize, newOrientation), newOrientation);
    }
    boolean areAgentsNeighbours(String agent, String neighbour);
    default Set<String> getAgentNeighbours(String agent) {
        return getAllAgents().stream()
                .filter(it -> !it.equals(agent))
                .filter(other -> areAgentsNeighbours(agent, other))
                .collect(Collectors.toSet());
    }
    int getActionDelayMillis();
    void setActionDelayMillis(int delayMillis);
    double getSlideProbability();
    void setSlideProbability(double value);
}