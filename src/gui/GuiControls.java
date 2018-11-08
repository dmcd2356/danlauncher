/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.util.HashMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;

/**
 *
 * @author dan
 */
public class GuiControls {
  
  private static final int GAPSIZE = 4; // gap size to place on each side of each widget
  
  private static final Dimension SCREEN_SIZE = Toolkit.getDefaultToolkit().getScreenSize();
    
  public enum Orient { NONE, LEFT, RIGHT, CENTER }
  
  public enum FrameSize { NOLIMIT, FIXEDSIZE, FULLSCREEN }

  // types of controls for user-entry
  public enum InputControl { Label, TextField, CheckBox, RadioButton, ComboBox, Spinner, Button }
  
  private JFrame         mainFrame = null;
  private GridBagLayout  mainLayout = null;
  private Dimension      framesize = null;
  private final HashMap<String, JPanel>        gPanel = new HashMap();
  private final HashMap<String, JScrollPane>   gScrollPanel = new HashMap();
  private final HashMap<String, JTabbedPane>   gTabbedPanel = new HashMap();
  private final HashMap<String, JTextPane>     gTextPane = new HashMap();
  private final HashMap<String, JTable>        gTable = new HashMap<>();
  private final HashMap<String, JList>         gList = new HashMap();
  private final HashMap<String, JLabel>        gLabel = new HashMap();
  private final HashMap<String, JButton>       gButton = new HashMap();
  private final HashMap<String, JCheckBox>     gCheckbox = new HashMap();
  private final HashMap<String, JComboBox>     gCombobox = new HashMap();
  private final HashMap<String, JTextField>    gTextField = new HashMap();
  private final HashMap<String, JRadioButton>  gRadiobutton = new HashMap();
  private final HashMap<String, JSpinner>      gSpinner = new HashMap();
  private final HashMap<String, SplitInfo>     gSplitPane = new HashMap();
  
  private class SplitInfo {
    public String           name;
    public JSplitPane       pane;
    public boolean          horiz;
    public SplitComponent[] comp = { new SplitComponent(), new SplitComponent() };

    public class SplitComponent {
      public String    name;
      public Component panel;
    }
  }
  
  public GuiControls() {
  }
  
  public GuiControls(String title, int height, int width) {
    // limit height and width to max of screen dimensions
    height = (height > SCREEN_SIZE.height) ? SCREEN_SIZE.height : height;
    width  = (width  > SCREEN_SIZE.width)  ? SCREEN_SIZE.width  : width;

    framesize = new Dimension(height, width);
    mainFrame = new JFrame(title);
    mainFrame.setSize(framesize);
    mainFrame.setMinimumSize(framesize);

    // setup the layout for the frame
    mainLayout = new GridBagLayout();
    mainFrame.setFont(new Font("SansSerif", Font.PLAIN, 14));
    mainFrame.setLayout(mainLayout);
  }
  
  public JFrame newFrame(String title, double portion) {
    if (mainFrame == null) {
      if (portion < 0.2) {
        portion = 0.2;
      }
      if (portion > 1.0) {
        portion = 1.0;
      }
      framesize = new Dimension((int)(portion * (double)SCREEN_SIZE.height),
                                (int)(portion * (double)SCREEN_SIZE.width));
      mainFrame = new JFrame(title);
      mainFrame.setSize(framesize);
      mainFrame.setMinimumSize(framesize);
      mainFrame.setMaximumSize(framesize);
      mainFrame.setResizable(false);

      // setup the layout for the frame
      mainLayout = new GridBagLayout();
      mainFrame.setFont(new Font("SansSerif", Font.PLAIN, 14));
      mainFrame.setLayout(mainLayout);
    }
    return mainFrame;
  }

  public JFrame newFrame(String title, int height, int width, FrameSize size) {
    if (mainFrame == null) {
      // limit height and width to max of screen dimensions
      height = (height > SCREEN_SIZE.height) ? SCREEN_SIZE.height : height;
      width  = (width  > SCREEN_SIZE.width)  ? SCREEN_SIZE.width  : width;

      framesize = new Dimension(height, width);
      mainFrame = new JFrame(title);
      mainFrame.setSize(framesize);
      mainFrame.setMinimumSize(framesize);
      switch (size) {
        case FIXEDSIZE:
          mainFrame.setMaximumSize(framesize);
          mainFrame.setResizable(false);
          break;
        case FULLSCREEN:
          mainFrame.setState(Frame.MAXIMIZED_BOTH);
          break;
      }

      // setup the layout for the frame
      mainLayout = new GridBagLayout();
      mainFrame.setFont(new Font("SansSerif", Font.PLAIN, 14));
      mainFrame.setLayout(mainLayout);
    }
    return mainFrame;
  }
  
  public Dimension getFrameSize() {
    return mainFrame.getSize();
  }
  
  public void display() {
    if (mainFrame != null) {
      mainFrame.pack();
      mainFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      mainFrame.setLocationRelativeTo(null);
      mainFrame.setVisible(true);
    }
  }

  public void hide() {
    if (mainFrame != null) {
      mainFrame.setVisible(false);
    }
  }

  public void update() {
    if (mainFrame != null) {
      mainFrame.repaint();
    }
  }
  
  public void repack() {
    if (mainFrame != null) {
      mainFrame.pack();
      mainFrame.setSize(framesize);
    }
  }
  
  public void close() {
    gPanel.clear();
    gScrollPanel.clear();
    gTabbedPanel.clear();
    gTextPane.clear();
    gList.clear();
    gLabel.clear();
    gButton.clear();
    gCheckbox.clear();
    gCombobox.clear();
    gTextField.clear();
    gRadiobutton.clear();
    gSpinner.clear();
    
    if (mainFrame != null) {
      mainFrame.dispose();
      mainFrame = null;
    }
  }
  
  public JFrame getFrame() {
    return mainFrame;
  }

  public boolean isValidFrame() {
    return mainFrame != null;
  }

  public JPanel getPanel(String name) {
    if (gPanel == null) {
      return null;
    }
    return gPanel.get(name);
  }

  public JScrollPane getScrollPanel(String name) {
    if (gScrollPanel == null) {
      return null;
    }
    return gScrollPanel.get(name);
  }

  public JTabbedPane getTabbedPanel(String name) {
    if (gTabbedPanel == null) {
      return null;
    }
    return gTabbedPanel.get(name);
  }

  public JTable getTable(String name) {
    if (gTable == null) {
      return null;
    }
    return gTable.get(name);
  }

  public JTextPane getTextPane(String name) {
    if (gTextPane == null) {
      return null;
    }
    return gTextPane.get(name);
  }

  public JList getList(String name) {
    if (gList == null) {
      return null;
    }
    return gList.get(name);
  }

  public JLabel getLabel(String name) {
    if (gLabel == null) {
      return null;
    }
    return gLabel.get(name);
  }

  public JButton getButton(String name) {
    if (gButton == null) {
      return null;
    }
    return gButton.get(name);
  }

  public JCheckBox getCheckbox(String name) {
    if (gCheckbox == null) {
      return null;
    }
    return gCheckbox.get(name);
  }

  public JComboBox getCombobox(String name) {
    if (gCombobox == null) {
      return null;
    }
    return gCombobox.get(name);
  }

  public JTextField getTextField(String name) {
    if (gTextField == null) {
      return null;
    }
    return gTextField.get(name);
  }

  public JRadioButton getRadiobutton(String name) {
    if (gRadiobutton == null) {
      return null;
    }
    return gRadiobutton.get(name);
  }

  public JSpinner getSpinner(String name) {
    if (gSpinner == null) {
      return null;
    }
    return gSpinner.get(name);
  }

  public Component getInputDevice(String name, InputControl type) {
    switch(type) {
      case Label:
        return getLabel(name);
      case TextField:
        return getTextField(name);
      case CheckBox:
        return getCheckbox(name);
      case ComboBox:
        return getCombobox(name);
      case Spinner:
        return getSpinner(name);
      case Button:
        return getButton(name);
      default:
        System.err.println("Unhandled control type: " + type.toString());
        break;
    }
    return null;
  }
  
  public String getInputControl(String name, InputControl type) {
    // get the selected device
    Component control = getInputDevice(name, type);
    if (control == null) {
      System.err.println("ERROR: Control '" + name + "' of type " + type.toString() + " not found");
      return "";
    }
    
    // read the value from it and return as a String
    switch(type) {
      case Label:
        return ((JLabel)control).getText();
      case TextField:
        return ((JTextField)control).getText();
      case CheckBox:
        return "" + ((JCheckBox)control).isSelected();
      case ComboBox:
        String val = (String) ((JComboBox)control).getSelectedItem();
        return (val == null) ? "" : val;
      case Spinner:
        return (String) ((JSpinner)control).getValue();
      default:
        System.err.println("Unhandled control type: " + type.toString());
        break;
    }
    return "";
  }
  
  public void setInputControl(String name, InputControl type, String value) {
    // get the selected device
    Component control = getInputDevice(name, type);
    if (control == null) {
      System.err.println("ERROR: Control '" + name + "' of type " + type.toString() + " not found");
      return;
    }
    
    switch(type) {
      case Label:
        ((JLabel)control).setText(value);
        break;
      case TextField:
        ((JTextField)control).setText(value);
        break;
      case CheckBox:
        ((JCheckBox)control).setSelected(value.equals("true"));
        break;
      case ComboBox:
        ((JComboBox)control).setSelectedItem(value);
        break;
      case Spinner:
        // NOTE: this only handles integer spinners
        Integer intval;
        try {
          intval = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
          System.err.println("ERROR: Control '" + name + "' set to invalid numeric value: " + value);
          return;
        }
        ((JSpinner)control).setValue(intval);
        break;
      default:
        System.err.println("Unhandled control type: " + type.toString());
        break;
    }
  }
  
  /**
   * This modifies the dimensions of the specified JPanel.
   * 
   * @param panelname - the name of the jPanel container to modify
   * @param height  - minimum height of panel
   * @param width   - minimum width of panel
   * @return the panel
   */
  public JPanel setPanelSize(String panelname, int height, int width) {
    // limit height and width to max of screen dimensions
    height = (height > SCREEN_SIZE.height) ? SCREEN_SIZE.height : height;
    width  = (width  > SCREEN_SIZE.width)  ? SCREEN_SIZE.width  : width;

    JPanel panel = getPanel(panelname);
    Dimension fsize = new Dimension(height, width);
    panel.setSize(fsize);
    panel.setPreferredSize(fsize);
    panel.setMinimumSize(fsize);
    return panel;
  }
  
  /**
   * this sets up the gridbag constraints for a single panel to fill the container
   * 
   * @return the constraints
   */
  private GridBagConstraints setGbagConstraintsPanel() {
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(GAPSIZE, GAPSIZE, GAPSIZE, GAPSIZE);

    c.fill = GridBagConstraints.BOTH;
    c.gridwidth  = GridBagConstraints.REMAINDER;
    // since only 1 component, these both have to be non-zero for grid bag to work
    c.weightx = 1.0;
    c.weighty = 1.0;
    return c;
  }

  /**
   * This sets up the gridbag constraints for a simple element
   * 
   * @param pos - the orientation on the line
   * @param end - true if this is the last (or only) entry on the line
   * @return the constraints
   */
  private GridBagConstraints setGbagConstraints(Orient pos, boolean end) {
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(GAPSIZE, GAPSIZE, GAPSIZE, GAPSIZE);

    switch(pos) {
      case LEFT:
        c.anchor = GridBagConstraints.BASELINE_LEADING;
        break;
      case RIGHT:
        c.anchor = GridBagConstraints.BASELINE_TRAILING;
        break;
      case CENTER:
        c.anchor = GridBagConstraints.CENTER;
        break;
      case NONE:
        c.fill = GridBagConstraints.BOTH;
        c.gridwidth  = GridBagConstraints.REMAINDER;
        // since only 1 component, these both have to be non-zero for grid bag to work
        c.weightx = 1.0;
        c.weighty = 1.0;
        return c;
    }
    c.fill = GridBagConstraints.NONE;
    
    c.gridheight = 1;
    if (end) {
      c.gridwidth = GridBagConstraints.REMAINDER; //end row
    }
    return c;
  }

  /**
   * This sets up the gridbag constraints for an element on a line and places a label to the left
   * 
   * @param panel     - the panel to place the element in (null if place in frame)
   * @param gridbag   - the gridbag layout
   * @param pos       - the orientation on the line
   * @param end       - true if this is the last (or only) entry on the line
   * @param fullline  - true if take up entire line with item
   * @param title     - name of label to add
   * @return the constraints
   */
  private GridBagConstraints setGbagInsertLabel(JPanel panel, GridBagLayout gridbag,
                             Orient pos, boolean end, boolean fullline, String name, String title) {
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(GAPSIZE, GAPSIZE, GAPSIZE, GAPSIZE);
    
    switch(pos) {
      case LEFT:
        c.anchor = GridBagConstraints.BASELINE_LEADING;
        break;
      case RIGHT:
        c.anchor = GridBagConstraints.BASELINE_TRAILING;
        break;
      case CENTER:
        c.anchor = GridBagConstraints.CENTER;
        break;
      case NONE:
        break;
    }
    c.fill = GridBagConstraints.NONE;
    JLabel label = new JLabel(title);
    gridbag.setConstraints(label, c);
    if (panel != null) {
      panel.add(label);
    } else {
      mainFrame.add(label);
    }
    
    if (fullline) {
      c.weightx = 1.0;
      c.fill = GridBagConstraints.HORIZONTAL;
    } else {
      c.weightx = 50.0;
    }
    if (end) {
      c.gridwidth = GridBagConstraints.REMAINDER; //end row
    }
    if (name != null && !name.isEmpty()) {
      gLabel.put(name, label);
    }

    return c;
  }

  private JPanel getSelectedPanel(String panelname) {
    // get container panel if specified
    JPanel panel = null;
    if (panelname != null && !panelname.isEmpty()) {
      panel = gPanel.get(panelname);
      if (panel == null) {
        System.err.println("'" + panelname + "' panel not found!");
        System.exit(1);
      }
    }
    return panel;
  }
  
  /**
   * This adds the specified panel component to the main frame.
   * 
   * @param component - component to place the main frame
   */
  public void addToPanelFrame(JComponent component) {
    if (mainFrame == null || mainLayout == null) {
      return;
    }
    GridBagLayout gridbag = mainLayout;
    gridbag.setConstraints(component, setGbagConstraintsPanel());
    mainFrame.add(component);
  }

  /**
   * This creates an empty JLabel and places it in the container to add a vertical gap between components.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   */
  public void makeLineGap(String panelname) {
    if (mainFrame == null || mainLayout == null) {
      return;
    }

    JLabel label = new JLabel("");

    GridBagLayout gridbag;
    if (panelname != null) {
      JPanel panel = getSelectedPanel(panelname);
      gridbag = (GridBagLayout) panel.getLayout();
      gridbag.setConstraints(label, setGbagConstraints(Orient.LEFT, true));
      panel.add(label);
    } else {
      gridbag = mainLayout;
      gridbag.setConstraints(label, setGbagConstraints(Orient.LEFT, true));
      mainFrame.add(label);
    }
  }
  
  /**
   * This creates an empty JLabel and places it in the container to add a horizontal gap between components.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param width - width in pixels for the gap (the width of the dummy label)
   */
  public void makeGap(String panelname, int width) {
    if (mainFrame == null || mainLayout == null) {
      return;
    }

    JLabel label = new JLabel("");
    Dimension dim = new Dimension(25, width);
    label.setPreferredSize(dim);
    label.setMinimumSize(dim);

    GridBagLayout gridbag;
    if (panelname != null) {
      JPanel panel = getSelectedPanel(panelname);
      gridbag = (GridBagLayout) panel.getLayout();
      gridbag.setConstraints(label, setGbagConstraints(Orient.LEFT, false));
      panel.add(label);
    } else {
      gridbag = mainLayout;
      gridbag.setConstraints(label, setGbagConstraints(Orient.LEFT, false));
      mainFrame.add(label);
    }
  }
  
  /**
   * This creates a JLabel and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component (optional)
   * @param title   - the name to display as a label preceeding the widget
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   */
  public void makeLabel(String panelname, String name, String title, Orient pos, boolean end) {
    if (mainFrame == null || mainLayout == null) {
      return;
    }
    if (name != null && !name.isEmpty() && gLabel.containsKey(name)) {
      System.err.println("'" + name + "' label already added to container!");
      System.exit(1);
    }

    // get container panel if specified & get corresponding layout
    JPanel panel = getSelectedPanel(panelname);
    GridBagLayout gridbag = mainLayout;
    if (panel != null) {
      gridbag = (GridBagLayout) panel.getLayout();
    }

    // create the component
    JLabel label = new JLabel(title);
    gridbag.setConstraints(label, setGbagConstraints(pos, end));

    // place component in container & add entry to components list
    if (panel != null) {
      panel.add(label);
    } else {
      mainFrame.add(label);
    }

    if (name != null && !name.isEmpty()) {
      gLabel.put(name, label);
    }
  }

  /**
   * This creates a JLabel and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   */
  public void makePlaceholder(String panelname, Orient pos, boolean end) {
    makeLabel(panelname, "", "    ", pos, end);
  }
  
  /**
   * This creates a JButton and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @return the button widget
   */
  public JButton makeButton(String panelname, String name, String title, Orient pos, boolean end) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gButton.containsKey(name)) {
      System.err.println("'" + name + "' button already added to container!");
      System.exit(1);
    }

    // get the layout for the container
    GridBagLayout gridbag = mainLayout;
    JPanel panel = null;
    if (panelname != null && !panelname.isEmpty()) {
      panel = getPanel(panelname);
      if (panel == null) {
        System.err.println("'" + panelname + "' panel not found!");
        System.exit(1);
      }
      gridbag = (GridBagLayout) panel.getLayout();
    }

    // create the component
    JButton button = new JButton(title);
    gridbag.setConstraints(button, setGbagConstraints(pos, end));

    // place component in container & add entry to components list
    if (panel != null) {
      panel.add(button);
    } else {
      mainFrame.add(button);
    }

    gButton.put(name, button);
    return button;
  }

  /**
   * This creates a JCheckBox and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @param value   - 0 to have checkbox initially unselected, any other value for selected
   * @return the checkbox widget
   */
  public JCheckBox makeCheckbox(String panelname, String name, String title, Orient pos,
              boolean end, int value) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gCheckbox.containsKey(name)) {
      System.err.println("'" + name + "' checkbox already added to container!");
      System.exit(1);
    }

    // get the layout for the container
    GridBagLayout gridbag = mainLayout;
    JPanel panel = null;
    if (panelname != null && !panelname.isEmpty()) {
      panel = getPanel(panelname);
      if (panel == null) {
        System.err.println("'" + panelname + "' panel not found!");
        System.exit(1);
      }
      gridbag = (GridBagLayout) panel.getLayout();
    }

    // create the component
    JCheckBox cbox = new JCheckBox(title);
    cbox.setSelected(value != 0);
    gridbag.setConstraints(cbox, setGbagConstraints(pos, end));

    // place component in container & add entry to components list
    if (panel != null) {
      panel.add(cbox);
    } else {
      mainFrame.add(cbox);
    }

    gCheckbox.put(name, cbox);
    return cbox;
  }

  /**
   * This creates a JTextField and places it in the container.
   * These are single line String displays.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @param value   - 0 to have checkbox initially unselected, any other value for selected
   * @param length  - length of text field in chars
   * @param writable - true if field is writable by user, false if display only
   * @return the checkbox widget
   */
  public JTextField makeTextField(String panelname, String name, String title, Orient pos,
                boolean end, String value, int length, boolean writable) {
    
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gTextField.containsKey(name)) {
      System.err.println("'" + name + "' textfield already added to container!");
      System.exit(1);
    }

    // get the layout for the container
    GridBagLayout gridbag = mainLayout;
    JPanel panel = null;
    if (panelname != null && !panelname.isEmpty()) {
      panel = getPanel(panelname);
      if (panel == null) {
        System.err.println("'" + panelname + "' panel not found!");
        System.exit(1);
      }
      gridbag = (GridBagLayout) panel.getLayout();
    }

    // insert a label before the component
    GridBagConstraints c;
    if (title.isEmpty()) {
      c = setGbagConstraints(pos, end);
    } else {
      c = setGbagInsertLabel(panel, gridbag, pos, end, true, name, title);
    }
    
    // create the component
    Dimension size = new Dimension(length * 8, 25); // assume 6 pixels per char
    JTextField field = new JTextField();
    field.setText(value);
    field.setPreferredSize(size);
    field.setMinimumSize(size);
    field.setEditable(writable);
    gridbag.setConstraints(field, c);

    // place component in container & add entry to components list
    if (panel != null) {
      panel.add(field);
    } else {
      mainFrame.add(field);
    }

    gTextField.put(name, field);
    return field;
  }

  /**
   * This creates a JRadioButton and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @param value   - 0 to have checkbox initially unselected, any other value for selected
   * @return the checkbox widget
   */
  public JRadioButton makeRadiobutton(String panelname, String name, String title, Orient pos,
              boolean end, int value) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gRadiobutton.containsKey(name)) {
      System.err.println("'" + name + "' radiobutton already added to container!");
      System.exit(1);
    }

    // get the layout for the container
    GridBagLayout gridbag = mainLayout;
    JPanel panel = null;
    if (panelname != null && !panelname.isEmpty()) {
      panel = getPanel(panelname);
      if (panel == null) {
        System.err.println("'" + panelname + "' panel not found!");
        System.exit(1);
      }
      gridbag = (GridBagLayout) panel.getLayout();
    }

    // create the component
    JRadioButton rbutton = new JRadioButton(title);
    rbutton.setSelected(value != 0);
    gridbag.setConstraints(rbutton, setGbagConstraints(pos, end));

    // place component in container & add entry to components list
    if (panel != null) {
      panel.add(rbutton);
    } else {
      mainFrame.add(rbutton);
    }

    gRadiobutton.put(name, rbutton);
    return rbutton;
  }

  /**
   * This creates a JComboBox and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @return the combo widget
   */
  public JComboBox makeCombobox(String panelname, String name, String title, Orient pos, boolean end) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gCombobox.containsKey(name)) {
      System.err.println("'" + name + "' combobox already added to container!");
      System.exit(1);
    }

    // get the layout for the container
    GridBagLayout gridbag = mainLayout;
    JPanel panel = null;
    if (panelname != null && !panelname.isEmpty()) {
      panel = getPanel(panelname);
      if (panel == null) {
        System.err.println("'" + panelname + "' panel not found!");
        System.exit(1);
      }
      gridbag = (GridBagLayout) panel.getLayout();
    }

    // insert a label before the component
    GridBagConstraints c = setGbagInsertLabel(panel, gridbag, pos, end, true, name, title);
    
    // create the component
    JComboBox combobox = new JComboBox();
    gridbag.setConstraints(combobox, c);

    // place component in container & add entry to components list
    if (panel != null) {
      panel.add(combobox);
    } else {
      mainFrame.add(combobox);
    }

    gCombobox.put(name, combobox);
    return combobox;
  }
  
  /**
   * This creates an integer JSpinner and places it in the container.
   * Step size (increment/decrement value) is always set to 1.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @param minval  - the min range limit for the spinner
   * @param maxval  - the max range limit for the spinner
   * @param step    - step size for the spinner
   * @param curval  - the current value for the spinner
   * @return the spinner widget
   */
  public JSpinner makeSpinner(String panelname, String name, String title, Orient pos, boolean end,
          int minval, int maxval, int step, int curval) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gSpinner.containsKey(name)) {
      System.err.println("'" + name + "' spinner already added to container!");
      System.exit(1);
    }

    // get the layout for the container
    GridBagLayout gridbag = mainLayout;
    JPanel panel = null;
    if (panelname != null && !panelname.isEmpty()) {
      panel = getPanel(panelname);
      if (panel == null) {
        System.err.println("'" + panelname + "' panel not found!");
        System.exit(1);
      }
      gridbag = (GridBagLayout) panel.getLayout();
    }

    // insert a label before the component
    GridBagConstraints c = setGbagInsertLabel(panel, gridbag, pos, end, true, name, title);
    
    // create the component
    JSpinner spinner = new JSpinner();
    spinner.setModel(new SpinnerNumberModel(curval, minval, maxval, step));
    gridbag.setConstraints(spinner, c);

    // place component in container & add entry to components list
    if (panel != null) {
      panel.add(spinner);
    } else {
      mainFrame.add(spinner);
    }

    gSpinner.put(name, spinner);
    return spinner;
  }
  
  /**
   * This creates an empty JPanel and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget (null if no border)
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @return the panel
   */
  public JPanel makePanel(String panelname, String name, String title, Orient pos, boolean end) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gPanel.containsKey(name)) {
      System.err.println("'" + name + "' panel already added to container!");
      System.exit(1);
    }

    // get the layout for the container
    GridBagLayout gridbag = mainLayout;
    JPanel panel = null;
    if (panelname != null && !panelname.isEmpty()) {
      panel = getPanel(panelname);
      if (panel == null) {
        System.err.println("'" + panelname + "' container panel not found!");
        System.exit(1);
      }
      gridbag = (GridBagLayout) panel.getLayout();
    }

    // create the panel and apply constraints
    JPanel newpanel = new JPanel();
    if (!title.isEmpty()) {
      newpanel.setBorder(BorderFactory.createTitledBorder(title));
    }

    // create a layout for inside the panel
    GridBagLayout gbag = new GridBagLayout();
    newpanel.setFont(new Font("SansSerif", Font.PLAIN, 14));
    newpanel.setLayout(gbag);

    // place component in container & add entry to components list
    if (panel != null) {
      panel.add(newpanel);
    } else {
      mainFrame.add(newpanel);
    }

    gridbag.setConstraints(newpanel, setGbagConstraints(pos, end));
    gPanel.put(name, newpanel);
    return newpanel;
  }

  /**
   * This creates an empty JPanel and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget (null if no border)
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @param height  - desired height of panel
   * @param width   - desired width of panel
   * @return the panel
   */
  public JPanel makePanel(String panelname, String name, String title, Orient pos, boolean end, int height, int width) {
    JPanel panel = makePanel(panelname, name, title, pos, end);
    if (panel != null) {
      // limit height and width to max of screen dimensions
      height = (height > SCREEN_SIZE.height) ? SCREEN_SIZE.height : height;
      width  = (width  > SCREEN_SIZE.width)  ? SCREEN_SIZE.width  : width;

      Dimension fsize = new Dimension(height, width);
      panel.setSize(fsize);
      panel.setPreferredSize(fsize);
      panel.setMinimumSize(fsize);
    }
    return panel;
  }

  /**
   * This creates an empty JTabbedPanel and places it in the container.
   * 
   * @param panelname - the name of the container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget (null if no border)
   * @param pos     - orientatition on the line: LEFT, RIGHT or CENTER
   * @param end     - true if this is last widget in the line
   * @return the panel
   */
  public JTabbedPane makeTabbedPanel(String panelname, String name, String title, Orient pos, boolean end) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gTabbedPanel.containsKey(name)) {
      System.err.println("'" + name + "' panel already added to container!");
      System.exit(1);
    }

    // get the layout for the container
    GridBagLayout gridbag = mainLayout;
    JPanel panel = null;
    if (panelname != null && !panelname.isEmpty()) {
      panel = getPanel(panelname);
      if (panel == null) {
        System.err.println("'" + panelname + "' container panel not found!");
        System.exit(1);
      }
      gridbag = (GridBagLayout) panel.getLayout();
    }

    // create the panel and apply constraints
    JTabbedPane newpanel = new JTabbedPane();
    if (title != null) {
      newpanel.setBorder(BorderFactory.createTitledBorder(title));
    }

    // place component in container & add entry to components list
    if (panel != null) {
      panel.add(newpanel);
    } else {
      mainFrame.add(newpanel);
    }

    gridbag.setConstraints(newpanel, setGbagConstraintsPanel());
    gTabbedPanel.put(name, newpanel);
    return newpanel;
  }

  /**
   * This creates an empty JTable and places it in the container.
   * 
   * @param panelname - the name of the container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a title (null if no border)
   * @return the panel
   */
  public JTable makeScrollTable(String panelname, String name, String title) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gTabbedPanel.containsKey(name)) {
      System.err.println("'" + name + "' panel already added to container!");
      System.exit(1);
    }

    // get the layout for the container
    GridBagLayout gridbag = mainLayout;
    JPanel panel = null;
    if (panelname != null && !panelname.isEmpty()) {
      panel = getPanel(panelname);
      if (panel == null) {
        System.err.println("'" + panelname + "' container panel not found!");
        System.exit(1);
      }
      gridbag = (GridBagLayout) panel.getLayout();
    }

    // create the table place it in a scroll pane
    JTable table = new JTable();
    JScrollPane spanel = new JScrollPane(table);
    spanel.setBorder(BorderFactory.createTitledBorder(title));
    gridbag.setConstraints(spanel, setGbagConstraintsPanel());

    // place component in container & add entry to components list
    if (panel != null) {
      panel.add(spanel);
    } else {
      mainFrame.add(spanel);
    }

//    gridbag.setConstraints(table, setGbagConstraintsPanel());
    gTable.put(name, table);
    return table;
  }
  
  /**
   * This creates a JScrollPane containing a JList of Strings and places it in the container.
   * A List of String entries is passed to it that can be manipulated (adding & removing entries
   * that will be automatically reflected in the scroll pane.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget
   * @param list    - the list of entries to associate with the panel
   * @return the JList corresponding to the list passed
   */
  public JList makeScrollList(String panelname, String name, String title, DefaultListModel list) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gScrollPanel.containsKey(name) || gList.containsKey(name)) {
      System.err.println("'" + name + "' scrolling list panel already added to container!");
      System.exit(1);
    }

    // get the layout for the container (frame, panel or tabbed panel)
    GridBagLayout gridbag = mainLayout;
    JPanel panel = null;
    JTabbedPane tabpanel = null;
    if (panelname != null && !panelname.isEmpty()) {
      panel = getPanel(panelname);
      if (panel != null) {
        gridbag = (GridBagLayout) panel.getLayout();
      } else {
        tabpanel = getTabbedPanel(panelname);
        if (tabpanel == null) {
          System.err.println("'" + panelname + "' container panel not found!");
          System.exit(1);
        }
      }
    }

    // create the scroll panel and apply constraints
    JScrollPane spanel = new JScrollPane();
    spanel.setBorder(BorderFactory.createTitledBorder(title));
    // ignore constraints for tabbed panel
    if (tabpanel == null) {
      gridbag.setConstraints(spanel, setGbagConstraintsPanel());
    }

    // create a list component for the scroll panel and assign the list model to it
    JList scrollList = new JList();
    spanel.setViewportView(scrollList);
    scrollList.setModel(list);

    // place component in container & add entry to components list
    if (panel != null) {
      panel.add(spanel);
    } else if (tabpanel != null) {
      tabpanel.add(spanel);
    } else {
      mainFrame.add(spanel);
    }

    gScrollPanel.put(name, spanel);
    gList.put(name, scrollList);
    return scrollList;
  }

  /**
   * This creates a JScrollPane containing a JTextPane for text and places it in the container.
   * 
   * @param panelname - the name of the jPanel container to place the component in (null if use main frame)
   * @param name    - the name id of the component
   * @param title     - the name to display as a label preceeding the widget
   * @return the text panel contained in the scroll panel
   */
  public JTextPane makeScrollText(String panelname, String name, String title) {
    if (mainFrame == null || mainLayout == null) {
      return null;
    }
    if (gScrollPanel.containsKey(name) || gTextPane.containsKey(name)) {
      System.err.println("'" + name + "' scrolling textpanel already added to container!");
      System.exit(1);
    }

    // get the layout for the container
    GridBagLayout gridbag = mainLayout;
    JPanel panel = null;
    if (panelname != null && !panelname.isEmpty()) {
      panel = getPanel(panelname);
      if (panel == null) {
        System.err.println("'" + panelname + "' container panel not found!");
        System.exit(1);
      }
      gridbag = (GridBagLayout) panel.getLayout();
    }

    // create a text panel component
    JTextPane tpanel = new JTextPane();

    // create the scroll panel and apply constraints
    JScrollPane spanel = new JScrollPane(tpanel);
    spanel.setBorder(BorderFactory.createTitledBorder(title));
    gridbag.setConstraints(spanel, setGbagConstraintsPanel());

    // place component in container & add entry to components list
    if (panel != null) {
      panel.add(spanel);
    } else {
      mainFrame.add(spanel);
    }

    gScrollPanel.put(name, spanel);
    gTextPane.put(name, tpanel);
    return tpanel;
  }

  /**
   * This creates a JScrollPane containing a JList of Strings (does not place it in container).
   * A List of String entries is passed to it that can be manipulated (adding & removing entries
   * that will be automatically reflected in the scroll pane.
   * 
   * @param name    - the name id of the component
   * @param title   - the name to display as a label preceeding the widget
   * @param list    - the list of entries to associate with the panel
   * @return the JScrollPane created
   */
  public JScrollPane makeRawScrollList(String name, String title, DefaultListModel list) {
    // create the scroll panel and title
    JScrollPane spanel = new JScrollPane();
    spanel.setBorder(BorderFactory.createTitledBorder(title));

    // create a list component for the scroll panel and assign the list model to it
    JList scrollList = new JList();
    spanel.setViewportView(scrollList);
    scrollList.setModel(list);

    // place component in container & add entry to components list
//      tabpanel.add(spanel);

    gScrollPanel.put(name, spanel);
    gList.put(name, scrollList);
    return spanel;
  }

  public JSplitPane makeSplitPane(String name, boolean horiz, double divider) {
    // make sure split pane not already defined
    if (gSplitPane.containsKey(name)) {
      System.err.println("ERROR: Split pane already has entry: " + name);
      System.exit(1);
    }
    
    // create the pane
    int type;
    if (horiz) {
      type = JSplitPane.HORIZONTAL_SPLIT;
    } else {
      type = JSplitPane.VERTICAL_SPLIT;
    }
    JSplitPane splitPane = new JSplitPane(type);
    splitPane.setOneTouchExpandable(true);
    splitPane.setDividerLocation(divider);

    // add entry to map
    SplitInfo splitInfo = new SplitInfo();
    splitInfo.name = name;
    splitInfo.pane = splitPane;
    splitInfo.horiz = horiz;
    gSplitPane.put(name, splitInfo);
    return splitPane;
  }

  public void setSplitDivider(String splitname, double ratio) {
    // make sure split panel exists
    if (!gSplitPane.containsKey(splitname)) {
      System.err.println("ERROR: Split pane not found: " + splitname);
      System.exit(1);
    }
    
    if (ratio >= 1.0 || ratio <= 0.0) {
      System.err.println("ERROR: Invalid ratio for split divider: " + ratio + " (setting to 0.5)");
      ratio = 0.5;
    }

    SplitInfo splitInfo = gSplitPane.get(splitname);
    splitInfo.pane.setDividerLocation(ratio);
  }

  public void addSplitComponent(String splitname, int index, String compname, Component panel, boolean scrollable) {
    // make sure split panel exists
    if (!gSplitPane.containsKey(splitname)) {
      System.err.println("ERROR: Split pane not found: " + splitname);
      System.exit(1);
    }
    
    // make sure component isn't already defined
    SplitInfo splitInfo = gSplitPane.get(splitname);
    index = index == 0 ? 0 : 1;
    SplitInfo.SplitComponent comp = splitInfo.comp[index];
    if (comp.panel != null) {
      System.err.println("ERROR: Split pane component already defined: " + index);
      System.exit(1);
    }

    // if scrollable, make a scroll pane and insert component in it
    if (scrollable) {
      panel = new JScrollPane(panel);
    }
    
    // add component to split pane
    splitInfo.pane.add(panel, index);

    // update component info in map
    comp.name = compname;
    comp.panel = panel;
  }


  public Component getSplitComponent(String splitname, int index, String compname) {
    // make sure split panel exists
    if (!gSplitPane.containsKey(splitname)) {
      System.err.println("ERROR: Split pane not found: " + splitname);
      System.exit(1);
    }
    
    // make sure component is defined
    SplitInfo splitInfo = gSplitPane.get(splitname);
    index = index == 0 ? 0 : 1;
    SplitInfo.SplitComponent comp = splitInfo.comp[index];
    if (comp.panel == null) {
      System.err.println("ERROR: Split pane component not defined: " + index);
      System.exit(1);
    }
    
    return comp.panel;
  }
  
  public static JFrame makeFrameWithText(String title, String text, int height, int width) {
    // define size of panel
    Dimension dim = new Dimension(height, width);

    // create a text panel component and place the text message in it
    JTextArea tpanel = new JTextArea();
    tpanel.setText(text);

    // create the scroll panel and place text panel in it
    JScrollPane spanel = new JScrollPane(tpanel);
    spanel.setBorder(BorderFactory.createEmptyBorder());

    // apply constraints
    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(GAPSIZE, GAPSIZE, GAPSIZE, GAPSIZE);
    c.fill = GridBagConstraints.BOTH;
    c.gridwidth  = GridBagConstraints.REMAINDER;
    // since only 1 component, these both have to be non-zero for grid bag to work
    c.weightx = 1.0;
    c.weighty = 1.0;
    gridbag.setConstraints(spanel, c);

    // create frame and put scroll panel in it
    JFrame frame = new JFrame();
    frame.setTitle(title);
    frame.setContentPane(spanel);
    frame.setSize(dim);
    frame.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    return frame;
  }

}
