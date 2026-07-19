package com.ktu.scholar.ui.todos;

import android.graphics.Paint;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import com.ktu.scholar.R;
import com.ktu.scholar.data.db.TodoEntity;

import java.util.List;

public class TodosAdapter extends RecyclerView.Adapter<TodosAdapter.VH> {

    public interface OnTodoAction {
        void onToggle(TodoEntity todo);
        void onDelete(TodoEntity todo);
    }

    private List<TodoEntity> data;
    private final boolean compact;
    private OnTodoAction listener;

    public TodosAdapter(OnTodoAction listener, boolean compact) {
        this.listener = listener;
        this.compact  = compact;
    }

    public void submitList(List<TodoEntity> list) {
        this.data = list;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = compact ? R.layout.item_todo_compact : R.layout.item_todo;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        TodoEntity t = data.get(pos);
        h.tvTitle.setText(t.title);
        if (t.completed) {
            h.tvTitle.setPaintFlags(h.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvTitle.setTextColor(0xFF8BA3C7);
        } else {
            h.tvTitle.setPaintFlags(h.tvTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvTitle.setTextColor(0xFFF0F4FF);
        }

        if (h.cbDone != null) {
            h.cbDone.setChecked(t.completed);
            h.cbDone.setOnCheckedChangeListener((btn, checked) -> {
                if (listener != null) { t.completed = checked; listener.onToggle(t); }
            });
        }
        if (h.tvDue != null) h.tvDue.setText(t.dueDate != null ? t.dueDate : "");
        if (h.btnDelete != null && listener != null) {
            h.btnDelete.setOnClickListener(v -> listener.onDelete(t));
        }
    }

    @Override public int getItemCount() { return data == null ? 0 : data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDue;
        CheckBox cbDone;
        ImageButton btnDelete;

        VH(View v) {
            super(v);
            tvTitle   = v.findViewById(R.id.tv_title);
            tvDue     = v.findViewById(R.id.tv_due);
            cbDone    = v.findViewById(R.id.cb_done);
            btnDelete = v.findViewById(R.id.btn_delete);
        }
    }
}
