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


}