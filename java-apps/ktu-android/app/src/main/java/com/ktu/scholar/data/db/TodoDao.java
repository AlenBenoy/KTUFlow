package com.ktu.scholar.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY created_at DESC")
    LiveData<List<TodoEntity>> observeAll();

    @Query("SELECT * FROM todos WHERE completed = 0 ORDER BY due_date ASC, created_at DESC")
    LiveData<List<TodoEntity>> observePending();

    @Query("SELECT * FROM todos ORDER BY created_at DESC")
    List<TodoEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TodoEntity todo);

    @Update
    void update(TodoEntity todo);

    @Query("DELETE FROM todos WHERE id = :id")
    void delete(String id);
}
