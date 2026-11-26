package env;

import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import jason.asSyntax.ListTerm;
import jason.asSyntax.Structure;
import jason.asSyntax.Term;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Arena2DEnvironmentTest {

    @Test
    public void perceptsReflectRolesAndHideReservedFiresFromDispatcher() {
        Fixture fixture = fixture();
        FireStationModelImpl model = fixture.model;
        Vector2D fire = Vector2D.of(4, 4);
        assertTrue(model.setAgentPose("dispatcher", 0, 0, Orientation.NORTH));
        assertTrue(model.setAgentPose("firefighter", 0, 1, Orientation.NORTH));
        assertTrue(model.setAgentPose("civilian", 5, 5, Orientation.NORTH));
        assertTrue(model.addFire(fire));

        Collection<Literal> firefighterPercepts = fixture.environment.getPercepts("firefighter");
        assertTrue(firefighterPercepts.contains(Literal.parseLiteral("home(0,1)")));
        assertTrue(firefighterPercepts.contains(Literal.parseLiteral("water_level(100)")));
        assertTrue(firefighterPercepts.contains(Literal.parseLiteral("water_capacity(100)")));
        assertTrue(firefighterPercepts.contains(Literal.parseLiteral("water_cost(25)")));
        assertTrue(hasFunctor(firefighterPercepts, "occupied"));

        Collection<Literal> civilianPercepts = fixture.environment.getPercepts("civilian");
        assertFalse(hasFunctor(civilianPercepts, "home"));
        assertFalse(hasFunctor(civilianPercepts, "water_level"));
        assertFalse(hasFunctor(civilianPercepts, "water_capacity"));
        assertFalse(hasFunctor(civilianPercepts, "water_cost"));
        assertFalse(hasFunctor(civilianPercepts, "occupied"));

        Collection<Literal> dispatcherPercepts = fixture.environment.getPercepts("dispatcher");
        assertTrue(hasFunctor(dispatcherPercepts, "fire_score"));
        assertTrue(hasFunctor(dispatcherPercepts, "fire_priority"));
        assertTrue(fixture.environment.executeAction("dispatcher",
                structure("reserve_fire", 4, 4)));
        dispatcherPercepts = fixture.environment.getPercepts("dispatcher");
        assertFalse(hasFunctor(dispatcherPercepts, "fire_score"));
        assertFalse(hasFunctor(dispatcherPercepts, "fire_priority"));
    }

    @Test
    public void firefighterOccupiedPerceptContainsOtherAgentsAndAllFiresButNotItself() {
        Fixture fixture = fixture();
        assertTrue(fixture.model.setAgentPose("firefighter", 1, 1, Orientation.NORTH));
        assertTrue(fixture.model.setAgentPose("firefighter2", 2, 1, Orientation.NORTH));
        assertTrue(fixture.model.addFire(Vector2D.of(3, 3)));

        Literal occupiedLiteral = findFunctor(
                fixture.environment.getPercepts("firefighter"), "occupied");
        ListTerm occupied = (ListTerm) occupiedLiteral.getTerm(0);

        assertTrue(occupied.contains(position(2, 1)));
        assertTrue(occupied.contains(position(3, 3)));
        assertFalse(occupied.contains(position(1, 1)));
    }

    @Test
    public void moveRejectsMismatchedOrigin() {
        Fixture fixture = firefighterAt(1, 1);
        assertFalse(fixture.environment.executeAction("firefighter",
                move(0, 1, 1, 2, 4, 4)));
    }

    @Test
    public void moveRejectsNonAdjacentDestination() {
        Fixture fixture = firefighterAt(1, 1);
        assertFalse(fixture.environment.executeAction("firefighter",
                move(1, 1, 3, 1, 4, 4)));
    }

    @Test
    public void moveRejectsDestinationOutsideGrid() {
        Fixture fixture = firefighterAt(7, 1);
        Vector2D originalPosition = fixture.model.getAgentPosition("firefighter");

        assertFalse(fixture.environment.executeAction("firefighter",
                move(7, 1, 8, 1, 4, 4)));
        assertEquals(originalPosition, fixture.model.getAgentPosition("firefighter"));
    }

    @Test
    public void moveRejectsFireThatIsNotTheTarget() {
        Fixture fixture = firefighterAt(1, 1);
        assertTrue(fixture.model.addFire(Vector2D.of(2, 1)));

        assertFalse(fixture.environment.executeAction("firefighter",
                move(1, 1, 2, 1, 4, 4)));
    }

    @Test
    public void moveAllowsEnteringTheTargetFire() {
        Fixture fixture = firefighterAt(1, 1);
        assertTrue(fixture.model.addFire(Vector2D.of(2, 1)));

        assertTrue(fixture.environment.executeAction("firefighter",
                move(1, 1, 2, 1, 2, 1)));
        assertEquals(Vector2D.of(2, 1), fixture.model.getAgentPosition("firefighter"));
    }

    @Test
    public void randomCivilianMovesNeverEnterFireOrStation() {
        Fixture fixture = fixture();
        Vector2D start = Vector2D.of(1, 1);
        assertTrue(fixture.model.setAgentPose("civilian", start, Orientation.NORTH));
        int blockedDirections = 0;
        for (Orientation direction : Orientation.values()) {
            Vector2D adjacent = start.afterStep(1, direction);
            assertTrue(fixture.model.isPositionInside(adjacent));
            if (!fixture.model.isStationPosition(adjacent)) {
                assertTrue(fixture.model.addFire(adjacent));
            }
            blockedDirections++;
        }
        assertEquals(Orientation.values().length, blockedDirections);

        for (int attempt = 0; attempt < 50; attempt++) {
            assertFalse(fixture.environment.executeAction("civilian",
                    ASSyntax.createStructure("move", ASSyntax.createAtom("random"))));
            Vector2D position = fixture.model.getAgentPosition("civilian");
            assertEquals(start, position);
            assertFalse(fixture.model.isStationPosition(position));
            assertFalse(fixture.model.getFires().contains(position));
        }
    }

    @Test
    public void reportFireSucceedsForAnAdjacentFreeNonStationCell() {
        Fixture fixture = fixture();
        assertTrue(fixture.model.setAgentPose("civilian", 2, 2, Orientation.NORTH));

        assertTrue(fixture.environment.executeAction("civilian",
                structure("report_fire", 3, 3)));
        assertTrue(fixture.model.getFires().contains(Vector2D.of(3, 3)));
    }

    @Test
    public void reportFireRejectsNonAdjacentCell() {
        Fixture fixture = fixture();
        assertTrue(fixture.model.setAgentPose("civilian", 2, 2, Orientation.NORTH));

        assertFalse(fixture.environment.executeAction("civilian",
                structure("report_fire", 4, 4)));
    }

    @Test
    public void reportFireRejectsCellOccupiedByAnAgent() {
        Fixture fixture = fixture();
        assertTrue(fixture.model.setAgentPose("civilian", 2, 2, Orientation.NORTH));
        assertTrue(fixture.model.setAgentPose("civilian2", 3, 3, Orientation.NORTH));

        assertFalse(fixture.environment.executeAction("civilian",
                structure("report_fire", 3, 3)));
    }

    @Test
    public void reportFireRejectsStationCell() {
        Fixture fixture = fixture();
        assertTrue(fixture.model.setAgentPose("civilian", 1, 1, Orientation.NORTH));

        assertFalse(fixture.environment.executeAction("civilian",
                structure("report_fire", 0, 1)));
    }

    @Test
    public void extinguishRejectsAgentOutsideTheSpecifiedFireCell() {
        Fixture fixture = firefighterAt(1, 1);
        assertTrue(fixture.model.addFire(Vector2D.of(2, 1)));

        assertFalse(fixture.environment.executeAction("firefighter",
                structure("extinguish", 2, 1)));
    }

    @Test
    public void extinguishRejectsInsufficientWaterOnFireCell() {
        Fixture fixture = firefighterAt(4, 4);
        for (int extinguished = 0; extinguished < 4; extinguished++) {
            Vector2D fire = Vector2D.of(2 + extinguished, 3);
            assertTrue(fixture.model.addFire(fire));
            assertTrue(fixture.model.setAgentPose("firefighter", fire, Orientation.NORTH));
            assertTrue(fixture.model.extinguishFire("firefighter"));
        }
        Vector2D finalFire = Vector2D.of(6, 3);
        assertTrue(fixture.model.addFire(finalFire));
        assertTrue(fixture.model.setAgentPose("firefighter", finalFire, Orientation.NORTH));

        assertFalse(fixture.environment.executeAction("firefighter",
                structure("extinguish", 6, 3)));
    }

    @Test
    public void refillRejectsAgentOutsideStation() {
        Fixture fixture = firefighterAt(3, 3);

        assertFalse(fixture.environment.executeAction("firefighter", ASSyntax.createStructure("refill")));
    }

    @Test
    public void reserveFireIsExclusiveAndReleaseIsIdempotent() {
        Fixture fixture = fixture();
        assertTrue(fixture.model.addFire(Vector2D.of(4, 4)));

        assertTrue(fixture.environment.executeAction("dispatcher",
                structure("reserve_fire", 4, 4)));
        assertFalse(fixture.environment.executeAction("dispatcher",
                structure("reserve_fire", 4, 4)));
        assertTrue(fixture.environment.executeAction("dispatcher",
                structure("release_fire", 4, 4)));
        assertTrue(fixture.environment.executeAction("dispatcher",
                structure("release_fire", 4, 4)));
    }

    private static Fixture firefighterAt(int x, int y) {
        Fixture fixture = fixture();
        assertTrue(fixture.model.setAgentPose("firefighter", x, y, Orientation.NORTH));
        return fixture;
    }

    private static Fixture fixture() {
        FireStationModelImpl model = new FireStationModelImpl(8, 8);
        Arena2DEnvironment environment = new Arena2DEnvironment();
        environment.initForTest(model, new NoOpView(model));
        assertEquals(16, model.getActionDelayMillis());
        return new Fixture(environment, model);
    }

    private static Structure move(int fromX, int fromY, int toX, int toY, int fireX, int fireY) {
        return structure("move", fromX, fromY, toX, toY, fireX, fireY);
    }

    private static Structure structure(String functor, int... values) {
        Term[] terms = new Term[values.length];
        for (int i = 0; i < values.length; i++) {
            terms[i] = ASSyntax.createNumber(values[i]);
        }
        return ASSyntax.createStructure(functor, terms);
    }

    private static Structure position(int x, int y) {
        return ASSyntax.createStructure("pos", ASSyntax.createNumber(x), ASSyntax.createNumber(y));
    }

    private static boolean hasFunctor(Collection<Literal> percepts, String functor) {
        return percepts.stream().anyMatch(percept -> functor.equals(percept.getFunctor()));
    }

    private static Literal findFunctor(Collection<Literal> percepts, String functor) {
        return percepts.stream()
                .filter(percept -> functor.equals(percept.getFunctor()))
                .findFirst()
                .orElseThrow(AssertionError::new);
    }

    private static final class Fixture {
        private final Arena2DEnvironment environment;
        private final FireStationModelImpl model;

        private Fixture(Arena2DEnvironment environment, FireStationModelImpl model) {
            this.environment = environment;
            this.model = model;
        }
    }

    private static final class NoOpView implements Arena2DView {
        private final FireStationModelImpl model;

        private NoOpView(FireStationModelImpl model) {
            this.model = model;
        }

        @Override
        public Arena2DModel getModel() {
            return model;
        }

        @Override
        public void notifyModelChanged() {
        }
    }
}
