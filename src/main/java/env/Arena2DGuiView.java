package env;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Arena2DGuiView extends JFrame implements Arena2DView {

    private static final Random RAND = new Random();

    private static Color randomColor() {
        return new Color(RAND.nextInt(200), RAND.nextInt(200), RAND.nextInt(200));
    }

    private static Color negateColor(Color color) {
        return new Color(255 - color.getRed(), 255 - color.getGreen(), 255 - color.getBlue(), color.getAlpha());
    }

    private FireStationModelImpl model;
    private final Map<Vector2D, JButton> buttonsGrid = new HashMap<>();
    private final Map<String, Color> agentColors = new HashMap<>();

    public Arena2DGuiView(FireStationModelImpl model) {
        this.model = model;

        JPanel contentPane = new JPanel(new BorderLayout());
        JPanel grid = new JPanel(new GridLayout(model.getHeight(), model.getWidth()));
        for (int y = 0; y < model.getHeight(); y++) {
            for (int x = 0; x < model.getWidth(); x++) {
                JButton b = new JButton(" ");
                b.setMargin(new Insets(2, 2, 2, 2));
                b.setFont(b.getFont().deriveFont(10f));
                grid.add(b);
                buttonsGrid.put(Vector2D.of(x, y), b);
            }
        }
        contentPane.add(grid, BorderLayout.CENTER);
        JPanel speedPanel = new JPanel(new BorderLayout(8, 0));
        speedPanel.add(new JLabel("Slower"), BorderLayout.WEST);
        JSlider slider = new JSlider(JSlider.HORIZONTAL, 1, 60, 50);
        slider.setMajorTickSpacing(10);
        slider.setPaintTicks(true);
        speedPanel.add(slider, BorderLayout.CENTER);
        speedPanel.add(new JLabel("Faster"), BorderLayout.EAST);
        contentPane.add(speedPanel, BorderLayout.SOUTH);
        model.setActionDelayMillis(3000 - (int) Math.round((slider.getValue() - 1) * (2984.0 / 59)));
        slider.addChangeListener(e -> {
            int delayMillis = 3000 - (int) Math.round((slider.getValue() - 1) * (2984.0 / 59));
            model.setActionDelayMillis(delayMillis);
        });
        setContentPane(contentPane);
        setTitle("Fire Station MAS Simulation");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    private Color getColorForAgent(String agent) {
        if (!agentColors.containsKey(agent)) {
            // Distinguiamo i colori in base al prefisso del nome dell'agente se vuoi
            agentColors.put(agent, randomColor());
        }
        return agentColors.get(agent);
    }

    private String getAgentCaption(String agent) {
        if (agent.startsWith("firefighter")) {
            return "firefighter " + agentNumber(agent, "firefighter");
        }
        if (agent.startsWith("civilian")) {
            return "civilian " + agentNumber(agent, "civilian");
        }
        return agent;
    }

    private String agentNumber(String agent, String type) {
        String suffix = agent.substring(type.length());
        return suffix.matches("\\d+") ? suffix : "1";
    }

    @Override
    public Arena2DModel getModel() {
        return model;
    }

    private void updateView() {
        // 1. Reset di tutte le celle
        buttonsGrid.forEach((pos, b) -> {
            b.setText(" ");
            if (model.isStationPosition(pos)) {
                b.setText("FIRESTATION");
                b.setBackground(Color.LIGHT_GRAY);
                b.setForeground(Color.BLACK);
            } else {
                b.setBackground(Color.WHITE);
                b.setForeground(UIManager.getColor("Button.foreground"));
            }
            b.setEnabled(true);
        });

        // 2. Disegna i fuochi
        model.getFires().forEach(firePos -> {
            JButton b = buttonsGrid.get(firePos);
            if (b != null) {
                b.setText("FIRE " + model.getFireNumber(firePos));
                b.setBackground(Color.RED);
                b.setForeground(Color.WHITE);
            }
        });

        Map<Vector2D, List<String>> agentsByPosition = new HashMap<>();
        model.getAllAgents().forEach(a -> {
            Vector2D pos = model.getAgentPosition(a);
            agentsByPosition.computeIfAbsent(pos, ignored -> new ArrayList<>()).add(a);
        });

        agentsByPosition.forEach((pos, agents) -> {
            JButton b = buttonsGrid.get(pos);
            if (b == null) {
                return;
            }
            agents.sort(String::compareTo);
            StringBuilder caption = new StringBuilder("<html><center>");
            if (model.isStationPosition(pos)) {
                caption.append("FIRESTATION<br>");
            }
            for (String agent : agents) {
                caption.append(getAgentCaption(agent))
                        .append(" ")
                        .append(model.getAgentDirection(agent).getSymbol());
                if (agent.startsWith("firefighter")) {
                    caption.append("<br>WATER ")
                            .append(model.getWaterLevel(agent))
                            .append("/")
                            .append(FireStationModelImpl.WATER_CAPACITY);
                }
                caption.append("<br>");
            }
            caption.append("</center></html>");
            b.setText(caption.toString());

            Color color = getColorForAgent(agents.get(0));
            b.setBackground(color);
            b.setForeground(negateColor(color));
            b.setEnabled(false);
        });
        repaint();
    }

    @Override
    public void notifyModelChanged() {
        try {
            SwingUtilities.invokeAndWait(this::updateView);
        } catch (InterruptedException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}