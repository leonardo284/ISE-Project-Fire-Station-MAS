package env;

import jason.asSyntax.ASSyntax;
import jason.asSyntax.ListTermImpl;
import jason.asSyntax.Literal;
import jason.asSyntax.Structure;
import jason.environment.Environment;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class Arena2DEnvironment extends Environment {

    public static final Literal moveRandom = Literal.parseLiteral("move(random)");

    static Logger logger = Logger.getLogger(Arena2DEnvironment.class.getName());

    // DICHIARIAMO IL MODELLO COME FireStationModelImpl (così riconosce i fuochi)
    private FireStationModelImpl model;
    private Arena2DView view;

    @Override
    public void init(final String[] args) {
        int w = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int h = args.length > 1 ? Integer.parseInt(args[1]) : 10;

        // INIZIALIZZAZIONE DIRETTA SENZA CLASSE ANONIMA
        this.model = new FireStationModelImpl(w, h);

        Arena2DGuiView guiView = new Arena2DGuiView(model);
        this.view = guiView;
        guiView.setVisible(true);
    }

    void initForTest(FireStationModelImpl model, Arena2DView view) {
        this.model = model;
        this.view = view;
        this.model.setActionDelayMillis(16);
    }

    private void notifyModelChangedToView() {
        view.notifyModelChanged();
    }

    private void initializeAgentIfNeeded(String agentName) {
        if (!model.containsAgent(agentName)) {
            if (agentName.startsWith("firefighter") || "dispatcher".equals(agentName)) {
                Vector2D stationSlot = model.getStationPosition(agentName);
                if (!model.setAgentPose(agentName, stationSlot, Orientation.NORTH)) {
                    throw new IllegalStateException("Cannot place agent at its station slot: " + agentName);
                }
            } else {
                Vector2D spawnPosition = model.getRandomCivilianPosition();
                if (!model.setAgentPose(agentName, spawnPosition, Orientation.random())) {
                    throw new IllegalStateException("Cannot place civilian at " + spawnPosition);
                }
            }
            informAgentsOfPerceptChanges();
            view.notifyModelChanged();
        }
    }

    @Override
    public Collection<Literal> getPercepts(String agName) {
        initializeAgentIfNeeded(agName);
        Collection<Literal> percepts = new HashSet<>();

        Vector2D pos = model.getAgentPosition(agName);
        percepts.add(Literal.parseLiteral(String.format("at(%d,%d)", pos.getX(), pos.getY())));
        percepts.add(Literal.parseLiteral(String.format("grid(%d,%d)", model.getWidth(), model.getHeight())));

        model.getStationPositions().forEach(stationPosition ->
                percepts.add(Literal.parseLiteral(String.format(
                        "station(%d,%d)", stationPosition.getX(), stationPosition.getY()))));

        if (agName.startsWith("firefighter")) {
            Vector2D home = model.getStationPosition(agName);
            percepts.add(Literal.parseLiteral(String.format("home(%d,%d)", home.getX(), home.getY())));
            percepts.add(Literal.parseLiteral(String.format("water_level(%d)", model.getWaterLevel(agName))));
            percepts.add(Literal.parseLiteral(String.format(
                    "water_capacity(%d)", FireStationModelImpl.WATER_CAPACITY)));
            percepts.add(Literal.parseLiteral(String.format(
                    "water_cost(%d)", FireStationModelImpl.WATER_PER_EXTINGUISH)));
            ListTermImpl occupied = new ListTermImpl();
            model.getAllAgents().stream()
                    .filter(other -> !other.equals(agName))
                    .sorted()
                    .map(model::getAgentPosition)
                    .forEach(position -> occupied.add(ASSyntax.createStructure("pos",
                            ASSyntax.createNumber(position.getX()),
                            ASSyntax.createNumber(position.getY()))));
            model.getFires().stream()
                    .sorted((first, second) -> {
                        int byX = Integer.compare(first.getX(), second.getX());
                        return byX != 0 ? byX : Integer.compare(first.getY(), second.getY());
                    })
                    .forEach(position -> occupied.add(ASSyntax.createStructure("pos",
                            ASSyntax.createNumber(position.getX()),
                            ASSyntax.createNumber(position.getY()))));
            percepts.add(ASSyntax.createLiteral("occupied", occupied));
        }

        model.getFires().forEach(firePos -> {
            percepts.add(Literal.parseLiteral(String.format("fire(%d,%d)", firePos.getX(), firePos.getY())));
        });

        if ("dispatcher".equals(agName)) {
            Set<Vector2D> unreservedFires = model.getUnreservedFires();
            unreservedFires.forEach(fire -> percepts.add(ASSyntax.createLiteral("fire_priority",
                    ASSyntax.createNumber(fire.getX()),
                    ASSyntax.createNumber(fire.getY()),
                    ASSyntax.createNumber(model.getFirePriority(fire)))));
            model.getAllAgents().stream()
                    .filter(agent -> agent.startsWith("firefighter"))
                    .sorted()
                    .forEach(firefighter -> {
                        unreservedFires.forEach(fire -> percepts.add(ASSyntax.createLiteral("fire_score",
                                ASSyntax.createAtom(firefighter),
                                ASSyntax.createNumber(fire.getX()),
                                ASSyntax.createNumber(fire.getY()),
                                ASSyntax.createNumber(model.getFireDispatchScore(firefighter, fire)))));
                    });
            model.getAllAgents().stream()
                    .filter(agent -> agent.startsWith("firefighter"))
                    .forEach(firefighter -> model.getNearestUnreservedFire(firefighter).ifPresent(fire -> {
                        percepts.add(Literal.parseLiteral(String.format(
                                "nearest_fire(%s,%d,%d)", firefighter, fire.getX(), fire.getY())));
                    }));
            model.getHighestPriorityUnreservedFire().ifPresent(fire ->
                    percepts.add(Literal.parseLiteral(String.format(
                            "priority_fire(%d,%d)", fire.getX(), fire.getY()))));
        }

        return percepts;
    }

    @Override
    public boolean executeAction(final String ag, final Structure action) {
        initializeAgentIfNeeded(ag);
        boolean result = false;

        String functor = action.getFunctor();

        if (action.equals(moveRandom)) {
            Direction rd = Direction.random();
            Orientation direction = model.getAgentDirection(ag).rotate(rd);
            Vector2D destination = model.getAgentPosition(ag).afterStep(1, direction);
            boolean civilian = ag.startsWith("civilian");
            if (!civilian || (!model.getFires().contains(destination) && !model.isStationPosition(destination))) {
                result = model.moveAgent(ag, 1, rd);
            }
        }
        else if (functor.equals("move")) {
            try {
                int fromX = (int) ((jason.asSyntax.NumberTerm) action.getTerm(0)).solve();
                int fromY = (int) ((jason.asSyntax.NumberTerm) action.getTerm(1)).solve();
                int toX = (int) ((jason.asSyntax.NumberTerm) action.getTerm(2)).solve();
                int toY = (int) ((jason.asSyntax.NumberTerm) action.getTerm(3)).solve();
                int fireX = (int) ((jason.asSyntax.NumberTerm) action.getTerm(4)).solve();
                int fireY = (int) ((jason.asSyntax.NumberTerm) action.getTerm(5)).solve();
                result = moveOneStep(ag, Vector2D.of(fromX, fromY), Vector2D.of(toX, toY),
                        Vector2D.of(fireX, fireY));
            } catch (Exception e) {
                logger.warning("Errore nel parsing dell'azione di movimento: " + e.getMessage());
            }
        }
        else if (functor.equals("move_towards")) {
            try {
                int x = (int) ((jason.asSyntax.NumberTerm) action.getTerm(0)).solve();
                int y = (int) ((jason.asSyntax.NumberTerm) action.getTerm(1)).solve();
                result = moveTowards(ag, Vector2D.of(x, y));
            } catch (Exception e) {
                logger.warning("Errore nel parsing della destinazione: " + e.getMessage());
            }
        }
        else if (functor.equals("report_fire")) {
            try {
                int x = (int) ((jason.asSyntax.NumberTerm) action.getTerm(0)).solve();
                int y = (int) ((jason.asSyntax.NumberTerm) action.getTerm(1)).solve();
                Vector2D firePosition = Vector2D.of(x, y);
                Vector2D callerPosition = model.getAgentPosition(ag);
                int distanceX = Math.abs(callerPosition.getX() - x);
                int distanceY = Math.abs(callerPosition.getY() - y);
                result = Math.max(distanceX, distanceY) == 1
                        && model.getAgentByPosition(firePosition).isEmpty()
                        && model.addFire(firePosition);
            } catch (Exception e) {
                logger.warning("Errore nel parsing delle coordinate del fuoco: " + e.getMessage());
            }
        }
        else if (functor.equals("extinguish")) {
            try {
                int x = (int) ((jason.asSyntax.NumberTerm) action.getTerm(0)).solve();
                int y = (int) ((jason.asSyntax.NumberTerm) action.getTerm(1)).solve();
                result = model.getAgentPosition(ag).equals(Vector2D.of(x, y))
                        && model.extinguishFire(ag);
            } catch (Exception e) {
                logger.warning("Errore nel parsing delle coordinate dello spegnimento: " + e.getMessage());
            }
        }
        else if (functor.equals("refill")) {
            result = model.refillWater(ag);
        }
        else if (functor.equals("reserve_fire")) {
            try {
                int x = (int) ((jason.asSyntax.NumberTerm) action.getTerm(0)).solve();
                int y = (int) ((jason.asSyntax.NumberTerm) action.getTerm(1)).solve();
                result = model.reserveFire(Vector2D.of(x, y));
            } catch (Exception e) {
                logger.warning("Errore nel parsing della riserva del fuoco: " + e.getMessage());
            }
        }
        else if (functor.equals("release_fire")) {
            try {
                int x = (int) ((jason.asSyntax.NumberTerm) action.getTerm(0)).solve();
                int y = (int) ((jason.asSyntax.NumberTerm) action.getTerm(1)).solve();
                result = model.releaseFire(Vector2D.of(x, y));
            } catch (Exception e) {
                logger.warning("Errore nel parsing del rilascio del fuoco: " + e.getMessage());
            }
        }
        else {
            logger.warning("Impossibile gestire l'azione: " + action);
        }

        try {
            long delay = model.getActionDelayMillis();
            if (ag.startsWith("firefighter")) {
                delay = Math.max(8, delay / 2);
            }
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        informAgentsOfPerceptChanges();
        notifyModelChangedToView();
        return result;
    }

    private void informAgentsOfPerceptChanges() {
        informAgsEnvironmentChanged(model.getAllAgents().toArray(new String[0]));
    }

    private boolean moveTowards(String agent, Vector2D destination) {
        if (!model.isPositionInside(destination)) {
            return false;
        }

        Vector2D start = model.getAgentPosition(agent);
        if (start.equals(destination)) {
            return true;
        }

        ArrayDeque<Vector2D> queue = new ArrayDeque<>();
        Map<Vector2D, Vector2D> previous = new HashMap<>();
        queue.add(start);
        previous.put(start, start);

        while (!queue.isEmpty() && !previous.containsKey(destination)) {
            Vector2D current = queue.removeFirst();
            for (Orientation orientation : Orientation.values()) {
                Vector2D next = current.afterStep(1, orientation);
                if (!model.isPositionInside(next) || previous.containsKey(next)) {
                    continue;
                }
                if (agent.startsWith("civilian") && model.isStationPosition(next)) {
                    continue;
                }
                if (model.getFires().contains(next)) {
                    continue;
                }
                if (!next.equals(destination)
                        && model.getAgentByPosition(next).filter(other -> !other.equals(agent)).isPresent()) {
                    continue;
                }
                previous.put(next, current);
                queue.addLast(next);
            }
        }

        if (!previous.containsKey(destination)) {
            return false;
        }

        Vector2D nextStep = destination;
        while (!previous.get(nextStep).equals(start)) {
            nextStep = previous.get(nextStep);
        }

        Vector2D step = nextStep.minus(start);
        Orientation orientation = orientationFor(step);
        return model.setAgentPose(agent, nextStep, orientation);
    }

    private boolean moveOneStep(String agent, Vector2D origin, Vector2D destination, Vector2D targetFire) {
        if (!agent.startsWith("firefighter")
                || !model.getAgentPosition(agent).equals(origin)
                || !model.isPositionInside(destination)
                || origin.equals(destination)
                || Math.abs(origin.getX() - destination.getX()) > 1
                || Math.abs(origin.getY() - destination.getY()) > 1
                || (model.getFires().contains(destination) && !destination.equals(targetFire))) {
            return false;
        }

        return model.setAgentPose(agent, destination, orientationFor(destination.minus(origin)));
    }

    private Orientation orientationFor(Vector2D step) {
        for (Orientation orientation : Orientation.values()) {
            if (orientation.asVector().equals(step)) {
                return orientation;
            }
        }
        throw new IllegalArgumentException("Invalid movement step: " + step);
    }

}