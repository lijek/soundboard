import javax.swing.*;
import java.awt.*;

import static java.awt.Toolkit.getDefaultToolkit;

// pierdoli się przezroczystość jak jest 1 lub 0 elementów i się ciągle przełącza — naprawa trudna // jednak działa poprawnie :)

public class Overlay extends JDialog {
    private final JScrollPane container;
    private final JList<Sound> selectionList;

    public Overlay(){
        Toolkit t = getDefaultToolkit();
        Dimension screenSize = t.getScreenSize();
        Dimension overallSize = new Dimension(screenSize.width / 4, screenSize.height / 54 * 5);

        setVisible(false);
        setUndecorated(true);
        setResizable(false);
        setAlwaysOnTop(true);
        setModal(false);
        setBackground(new Color(0, 0, 0, 0));
        setSize(overallSize);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        //setLayout(new BorderLayout());

        selectionList = new JList<>();
        selectionList.setEnabled(false);
        selectionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        selectionList.setLayoutOrientation(JList.VERTICAL);
        selectionList.setVisibleRowCount(5);
        selectionList.setFixedCellWidth(screenSize.width / 4);
        selectionList.setFixedCellHeight(screenSize.height / 54);
        selectionList.setBackground(new Color(0, 0, 255, 100));
        selectionList.setForeground(new Color(0, 255, 0));
        selectionList.setSelectionBackground(new Color(0, 255, 0, 100));
        selectionList.setSelectionForeground(new Color(0, 0, 255));
        selectionList.setCellRenderer(new DefaultListCellRenderer(){
            @Override
            public void setEnabled(boolean enabled) {
                super.setEnabled(true);
            }
        });

        container = new JScrollPane(selectionList);
        container.setOpaque(false);
        container.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        container.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        container.setBackground(new Color(0, 0, 0, 0));
        container.setBorder(null);


        //add(container, BorderLayout.NORTH);
        setContentPane(container);
    }

    public void setData(ListModel<Sound> dataModel){
        selectionList.setModel(dataModel);
        repaint();
    }

    public void setSelectionIndex(int index){
        container.getVerticalScrollBar().setValue((index - 2) * selectionList.getFixedCellHeight());
        selectionList.setSelectedIndex(index);
        repaint();
    }
}
