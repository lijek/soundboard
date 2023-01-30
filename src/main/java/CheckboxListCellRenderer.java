import javax.sound.sampled.Mixer;
import javax.swing.*;
import java.awt.*;

public class CheckboxListCellRenderer extends JCheckBox implements ListCellRenderer<Mixer.Info> {

    public Component getListCellRendererComponent(JList list, Mixer.Info value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {

        setComponentOrientation(list.getComponentOrientation());
        setFont(list.getFont());
        setBackground(list.getBackground());
        setForeground(list.getForeground());
        setSelected(isSelected);
        setEnabled(list.isEnabled());

        if (value != null) {
            setText(value.getName());
            setToolTipText("<html>" +
                                   "Description: " + value.getDescription() + "<br>" +
                                   "Vendor: " + value.getVendor() + "<br>" +
                                   "Version: " + value.getVersion() +
                           "</html>");
        }

        return this;
    }
}