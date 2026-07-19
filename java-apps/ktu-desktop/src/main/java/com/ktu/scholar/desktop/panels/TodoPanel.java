package com.ktu.scholar.desktop.panels;

import com.ktu.scholar.core.db.*;
import com.ktu.scholar.core.model.Todo;
import com.ktu.scholar.desktop.OnboardingDialog;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class TodoPanel extends BasePanel {

    private final DatabaseHelper db;
    private JPanel listPanel;
    private JTextField titleField;
    private JComboBox<Todo.Priority> priorityBox;
    private JComboBox<Todo.Category> categoryBox;
    private JTextField dueDateField;

    public TodoPanel(DatabaseHelper db) {
        super("Tasks");
        this.db = db;
        buildContent();
        loadTodos();
    }

    private void buildContent() {
        JPanel body = getBodyPanel();
        body.setLayout(new BorderLayout(16, 0));

        // ── Left: Add form ────────────────────────────────────────────
        JPanel formCard = new JPanel();
        formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));
        formCard.setBackground(CARD_BG);
        formCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)));
        formCard.setPreferredSize(new Dimension(280, 0));

        JLabel formTitle = new JLabel("Add Task");
        formTitle.setFont(HEADER_FONT);
        formTitle.setForeground(FG);
        formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleField = new JTextField();
        titleField.setBackground(new Color(0x1E3A5F));
        titleField.setForeground(FG);
        titleField.setCaretColor(FG);
        titleField.setFont(BODY_FONT);
        titleField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        titleField.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        priorityBox = new JComboBox<>(Todo.Priority.values());
        styleCombo(priorityBox);

        categoryBox = new JComboBox<>(Todo.Category.values());
        styleCombo(categoryBox);

        dueDateField = new JTextField("Due date (yyyy-MM-dd)");
        dueDateField.setBackground(new Color(0x1E3A5F));
        dueDateField.setForeground(MUTED);
        dueDateField.setCaretColor(FG);
        dueDateField.setFont(BODY_FONT);
        dueDateField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        dueDateField.setAlignmentX(Component.LEFT_ALIGNMENT);
        dueDateField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        dueDateField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                if (dueDateField.getForeground().equals(MUTED)) {
                    dueDateField.setText(""); dueDateField.setForeground(FG);
                }
            }
        });

        JButton addBtn = createPrimaryButton("Add Task");
        addBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        addBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        addBtn.addActionListener(e -> addTodo());

        formCard.add(formTitle);
        formCard.add(Box.createRigidArea(new Dimension(0, 16)));
        formCard.add(label("Title")); formCard.add(Box.createRigidArea(new Dimension(0, 4)));
        formCard.add(titleField);
        formCard.add(Box.createRigidArea(new Dimension(0, 12)));
        formCard.add(label("Priority")); formCard.add(Box.createRigidArea(new Dimension(0, 4)));
        formCard.add(priorityBox);
        formCard.add(Box.createRigidArea(new Dimension(0, 12)));
        formCard.add(label("Category")); formCard.add(Box.createRigidArea(new Dimension(0, 4)));
        formCard.add(categoryBox);
        formCard.add(Box.createRigidArea(new Dimension(0, 12)));
        formCard.add(label("Due Date")); formCard.add(Box.createRigidArea(new Dimension(0, 4)));
        formCard.add(dueDateField);
        formCard.add(Box.createRigidArea(new Dimension(0, 20)));
        formCard.add(addBtn);
        formCard.add(Box.createVerticalGlue());

        // ── Right: Task list ──────────────────────────────────────────
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);

        JScrollPane scroll = new JScrollPane(listPanel);
        styleScrollPane(scroll);

        body.add(formCard, BorderLayout.WEST);
        body.add(scroll,   BorderLayout.CENTER);
    }

    private void addTodo() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Title cannot be empty.");
            return;
        }
        Todo t = new Todo();
        t.setTitle(title);
        t.setPriority((Todo.Priority) priorityBox.getSelectedItem());
        t.setCategory((Todo.Category) categoryBox.getSelectedItem());
        String due = dueDateField.getText().trim();
        if (!due.isEmpty() && !due.startsWith("Due date")) t.setDueDate(due);

        try {
            new TodoRepository(db.getConnection()).insert(t);
            titleField.setText("");
            dueDateField.setText("Due date (yyyy-MM-dd)");
            dueDateField.setForeground(MUTED);
            loadTodos();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to add task: " + ex.getMessage());
        }
    }

    private void loadTodos() {
        SwingWorker<List<Todo>, Void> worker = new SwingWorker<>() {
            @Override protected List<Todo> doInBackground() throws Exception {
                return new TodoRepository(db.getConnection()).getAll();
            }
            @Override protected void done() {
                try { renderTodos(get()); } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private void renderTodos(List<Todo> todos) {
        listPanel.removeAll();
        if (todos.isEmpty()) {
            listPanel.add(emptyState("No tasks yet. Add one on the left!"));
        } else {
            for (Todo t : todos) listPanel.add(createTodoCard(t));
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel createTodoCard(Todo t) {
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(t.isCompleted() ? new Color(0x0F2A1C) : CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, priorityColor(t.getPriority())),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

        JCheckBox check = new JCheckBox();
        check.setSelected(t.isCompleted());
        check.setOpaque(false);
        check.addActionListener(e -> {
            t.setCompleted(check.isSelected());
            try { new TodoRepository(db.getConnection()).update(t); loadTodos(); }
            catch (Exception ex) { ex.printStackTrace(); }
        });

        JPanel info = new JPanel(new BorderLayout(0, 2));
        info.setOpaque(false);

        JLabel titleLbl = new JLabel(t.getTitle());
        titleLbl.setFont(BODY_FONT);
        titleLbl.setForeground(t.isCompleted() ? MUTED : FG);
        if (t.isCompleted()) titleLbl.setText("<html><strike>" + t.getTitle() + "</strike></html>");

        JLabel meta = new JLabel(t.getCategory().name() + (t.getDueDate() != null ? "  ·  " + t.getDueDate() : ""));
        meta.setFont(SMALL_FONT);
        meta.setForeground(MUTED);

        info.add(titleLbl, BorderLayout.NORTH);
        info.add(meta,     BorderLayout.SOUTH);

        JButton del = new JButton("✕");
        del.setForeground(DANGER);
        del.setBackground(CARD_BG);
        del.setBorderPainted(false);
        del.setFocusPainted(false);
        del.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        del.addActionListener(e -> {
            try { new TodoRepository(db.getConnection()).delete(t.getId()); loadTodos(); }
            catch (Exception ex) { ex.printStackTrace(); }
        });

        card.add(check, BorderLayout.WEST);
        card.add(info,  BorderLayout.CENTER);
        card.add(del,   BorderLayout.EAST);
        return card;
    }

    private Color priorityColor(Todo.Priority p) {
        return switch (p) {
            case HIGH   -> DANGER;
            case MEDIUM -> WARNING;
            case LOW    -> SUCCESS;
        };
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(SMALL_FONT);
        l.setForeground(MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private <T> void styleCombo(JComboBox<T> box) {
        box.setBackground(new Color(0x1E3A5F));
        box.setForeground(FG);
        box.setFont(BODY_FONT);
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
}
