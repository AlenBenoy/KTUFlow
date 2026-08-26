import React, { useState } from 'react';
import { StyleSheet, Text, View, ScrollView, RefreshControl, TouchableOpacity, TextInput } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useColors } from '@/hooks/useColors';
import {
  useGetTodos,
  useCreateTodo,
  useUpdateTodo,
  useDeleteTodo,
  useGetAnnouncements,
  useSyncAnnouncements,
  getGetTodosQueryKey,
  getGetAnnouncementsQueryKey,
} from '@workspace/api-client-react';
import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import * as Haptics from 'expo-haptics';

export default function TasksTab() {
  const colors = useColors();
  const queryClient = useQueryClient();
  
  const [activeTab, setActiveTab] = useState<'tasks' | 'announcements'>('tasks');
  const [taskFilter, setTaskFilter] = useState<'all' | 'pending' | 'completed'>('all');
  
  // For new task quick add
  const [newTaskTitle, setNewTaskTitle] = useState('');
  const [isAdding, setIsAdding] = useState(false);

  const { data: todosData, isLoading: isLoadingTodos } = useGetTodos({ status: taskFilter });
  const { data: announcementsData, isLoading: isLoadingAnnouncements } = useGetAnnouncements({ limit: 20 });
  
  const { mutate: createTodo } = useCreateTodo();
  const { mutate: updateTodo } = useUpdateTodo();
  const { mutate: deleteTodo } = useDeleteTodo();
  const { mutate: syncAnnouncements, isPending: isSyncingAnnouncements } = useSyncAnnouncements();

  const [refreshing, setRefreshing] = useState(false);

  const onRefresh = React.useCallback(() => {
    setRefreshing(true);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    
    if (activeTab === 'tasks') {
      queryClient.invalidateQueries({ queryKey: getGetTodosQueryKey({ status: taskFilter }) }).then(() => setRefreshing(false));
    } else {
      syncAnnouncements(undefined, {
        onSettled: () => {
          queryClient.invalidateQueries({ queryKey: getGetAnnouncementsQueryKey({ limit: 20 }) });
          setRefreshing(false);
        }
      });
    }
  }, [activeTab, taskFilter, syncAnnouncements, queryClient]);

  const toggleTodoComplete = (id: string, currentStatus: boolean) => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    
    // Optimistic update
    queryClient.setQueryData(getGetTodosQueryKey({ status: taskFilter }), (oldData: any) => {
      if (!oldData?.todos) return oldData;
      return {
        ...oldData,
        todos: oldData.todos.map((t: any) => t.id === id ? { ...t, completed: !currentStatus } : t)
      };
    });

    updateTodo(
      { id, data: { completed: !currentStatus } },
      {
        onSettled: () => {
          queryClient.invalidateQueries({ queryKey: getGetTodosQueryKey({ status: taskFilter }) });
        }
      }
    );
  };

  const handleAddTodo = () => {
    if (!newTaskTitle.trim()) {
      setIsAdding(false);
      return;
    }

    createTodo(
      { data: { title: newTaskTitle.trim(), priority: 'medium', category: 'personal' } },
      {
        onSuccess: () => {
          setNewTaskTitle('');
          setIsAdding(false);
          queryClient.invalidateQueries({ queryKey: getGetTodosQueryKey({ status: taskFilter }) });
        }
      }
    );
  };

  const getPriorityColor = (priority: string) => {
    if (priority === 'high') return colors.destructive;
    if (priority === 'medium') return colors.primary;
    return colors.accent;
  };

  const renderSegmentedControl = () => (
    <View style={[styles.segmentedControl, { backgroundColor: colors.muted }]}>
      <TouchableOpacity 
        style={[styles.segmentButton, activeTab === 'tasks' && { backgroundColor: colors.card, shadowColor: '#000', shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.1, shadowRadius: 2 }]}
        onPress={() => { Haptics.selectionAsync(); setActiveTab('tasks'); }}
      >
        <Text style={[styles.segmentText, { color: activeTab === 'tasks' ? colors.foreground : colors.mutedForeground }]}>Tasks</Text>
      </TouchableOpacity>
      <TouchableOpacity 
        style={[styles.segmentButton, activeTab === 'announcements' && { backgroundColor: colors.card, shadowColor: '#000', shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.1, shadowRadius: 2 }]}
        onPress={() => { Haptics.selectionAsync(); setActiveTab('announcements'); }}
      >
        <Text style={[styles.segmentText, { color: activeTab === 'announcements' ? colors.foreground : colors.mutedForeground }]}>Announcements</Text>
      </TouchableOpacity>
    </View>
  );

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]} edges={['top']}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.foreground }]}>{activeTab === 'tasks' ? 'Tasks' : 'Updates'}</Text>
      </View>

      <View style={{ paddingHorizontal: 20, marginBottom: 16 }}>
        {renderSegmentedControl()}
      </View>

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={colors.primary}
            colors={[colors.primary]}
          />
        }
      >
        {activeTab === 'tasks' ? (
          <View>
            <View style={styles.filterRow}>
              {['all', 'pending', 'completed'].map((f) => (
                <TouchableOpacity 
                  key={f} 
                  style={[
                    styles.filterChip, 
                    { backgroundColor: taskFilter === f ? colors.primary : colors.muted },
                    taskFilter === f && { borderColor: colors.primary, borderWidth: 1 }
                  ]}
                  onPress={() => setTaskFilter(f as any)}
                >
                  <Text style={[
                    styles.filterChipText, 
                    { color: taskFilter === f ? colors.primaryForeground : colors.mutedForeground }
                  ]}>
                    {f.charAt(0).toUpperCase() + f.slice(1)}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            {isAdding && (
              <View style={[styles.addTaskCard, { backgroundColor: colors.card, borderColor: colors.primary }]}>
                <TextInput
                  style={[styles.addTaskInput, { color: colors.foreground }]}
                  placeholder="What needs to be done?"
                  placeholderTextColor={colors.mutedForeground}
                  value={newTaskTitle}
                  onChangeText={setNewTaskTitle}
                  autoFocus
                  onSubmitEditing={handleAddTodo}
                  onBlur={() => { if (!newTaskTitle) setIsAdding(false); }}
                />
                <TouchableOpacity onPress={handleAddTodo} style={styles.addTaskButton}>
                  <Ionicons name="arrow-up-circle" size={32} color={colors.primary} />
                </TouchableOpacity>
              </View>
            )}

            {(!todosData?.todos || todosData.todos.length === 0) && !isAdding ? (
              <View style={[styles.emptyState, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Ionicons name="checkmark-done-circle-outline" size={48} color={colors.mutedForeground} style={{ marginBottom: 16 }} />
                <Text style={[styles.emptyTitle, { color: colors.foreground }]}>No Tasks</Text>
                <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
                  {taskFilter === 'completed' ? "You haven't completed any tasks yet." : "You're all caught up for now."}
                </Text>
              </View>
            ) : (
              <View style={styles.taskList}>
                {todosData?.todos.map((todo) => (
                  <View key={todo.id} style={[styles.taskCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                    <View style={[styles.priorityIndicator, { backgroundColor: getPriorityColor(todo.priority) }]} />
                    <TouchableOpacity 
                      style={styles.checkboxContainer}
                      onPress={() => toggleTodoComplete(todo.id, todo.completed)}
                    >
                      <Ionicons 
                        name={todo.completed ? "checkmark-circle" : "ellipse-outline"} 
                        size={28} 
                        color={todo.completed ? colors.primary : colors.mutedForeground} 
                      />
                    </TouchableOpacity>
                    <View style={styles.taskContent}>
                      <Text style={[
                        styles.taskTitle, 
                        { color: todo.completed ? colors.mutedForeground : colors.foreground },
                        todo.completed && { textDecorationLine: 'line-through' }
                      ]}>{todo.title}</Text>
                      <View style={styles.taskMeta}>
                        <View style={[styles.categoryBadge, { backgroundColor: colors.muted }]}>
                          <Text style={[styles.categoryText, { color: colors.mutedForeground }]}>{todo.category}</Text>
                        </View>
                        {todo.dueDate && (
                          <View style={styles.dateMeta}>
                            <Ionicons name="calendar-outline" size={12} color={colors.mutedForeground} />
                            <Text style={[styles.dateText, { color: colors.mutedForeground }]}>
                              {new Date(todo.dueDate).toLocaleDateString()}
                            </Text>
                          </View>
                        )}
                      </View>
                    </View>
                    <TouchableOpacity 
                      style={styles.deleteButton}
                      onPress={() => deleteTodo({ id: todo.id }, { onSuccess: () => queryClient.invalidateQueries({ queryKey: getGetTodosQueryKey({ status: taskFilter }) }) })}
                    >
                      <Ionicons name="trash-outline" size={20} color={colors.mutedForeground} />
                    </TouchableOpacity>
                  </View>
                ))}
              </View>
            )}
          </View>
        ) : (
          <View>
            {(!announcementsData?.announcements || announcementsData.announcements.length === 0) ? (
              <View style={[styles.emptyState, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Ionicons name="notifications-off-outline" size={48} color={colors.mutedForeground} style={{ marginBottom: 16 }} />
                <Text style={[styles.emptyTitle, { color: colors.foreground }]}>No Announcements</Text>
                <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
                  We'll check the KTU portal periodically.
                </Text>
              </View>
            ) : (
              <View style={styles.announcementList}>
                {announcementsData.announcements.map((ann) => (
                  <View key={ann.id} style={[styles.announcementCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                    <View style={styles.announcementHeader}>
                      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                        <View style={[styles.annCategoryBadge, { backgroundColor: colors.muted }]}>
                          <Text style={[styles.annCategoryText, { color: colors.mutedForeground }]}>{ann.category}</Text>
                        </View>
                        {ann.isNew && (
                          <View style={[styles.newBadge, { backgroundColor: colors.primary + '20' }]}>
                            <Text style={[styles.newBadgeText, { color: colors.primary }]}>NEW</Text>
                          </View>
                        )}
                      </View>
                      <Text style={[styles.annDate, { color: colors.mutedForeground }]}>
                        {new Date(ann.date).toLocaleDateString()}
                      </Text>
                    </View>
                    <Text style={[styles.annTitle, { color: colors.foreground }]}>{ann.title}</Text>
                    {ann.content && <Text style={[styles.annContent, { color: colors.mutedForeground }]} numberOfLines={3}>{ann.content}</Text>}
                  </View>
                ))}
              </View>
            )}
          </View>
        )}
        
        <View style={{ height: 100 }} />
      </ScrollView>

      {activeTab === 'tasks' && !isAdding && (
        <TouchableOpacity 
          style={[styles.fab, { backgroundColor: colors.primary, shadowColor: colors.primary }]}
          onPress={() => setIsAdding(true)}
        >
          <Ionicons name="add" size={32} color={colors.primaryForeground} />
        </TouchableOpacity>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 16,
  },
  title: {
    fontSize: 28,
    fontFamily: 'Inter_700Bold',
  },
  segmentedControl: {
    flexDirection: 'row',
    borderRadius: 8,
    padding: 4,
  },
  segmentButton: {
    flex: 1,
    paddingVertical: 8,
    alignItems: 'center',
    borderRadius: 6,
  },
  segmentText: {
    fontSize: 14,
    fontFamily: 'Inter_600SemiBold',
  },
  scrollContent: {
    padding: 20,
    paddingTop: 0,
  },
  filterRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 16,
  },
  filterChip: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
  },
  filterChipText: {
    fontSize: 13,
    fontFamily: 'Inter_500Medium',
  },
  emptyState: {
    padding: 32,
    borderRadius: 16,
    borderWidth: 1,
    alignItems: 'center',
    marginTop: 16,
  },
  emptyTitle: {
    fontSize: 20,
    fontFamily: 'Inter_600SemiBold',
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 15,
    fontFamily: 'Inter_400Regular',
    textAlign: 'center',
    lineHeight: 22,
  },
  taskList: {
    gap: 12,
  },
  taskCard: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    borderWidth: 1,
    overflow: 'hidden',
  },
  priorityIndicator: {
    width: 4,
    height: '100%',
  },
  checkboxContainer: {
    padding: 16,
  },
  taskContent: {
    flex: 1,
    paddingVertical: 16,
  },
  taskTitle: {
    fontSize: 16,
    fontFamily: 'Inter_500Medium',
    marginBottom: 6,
  },
  taskMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  categoryBadge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 4,
  },
  categoryText: {
    fontSize: 11,
    fontFamily: 'Inter_500Medium',
    textTransform: 'capitalize',
  },
  dateMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  dateText: {
    fontSize: 12,
    fontFamily: 'Inter_400Regular',
  },
  deleteButton: {
    padding: 16,
  },
  addTaskCard: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 16,
    marginBottom: 16,
  },
  addTaskInput: {
    flex: 1,
    height: 56,
    fontSize: 16,
    fontFamily: 'Inter_400Regular',
  },
  addTaskButton: {
    padding: 8,
  },
  fab: {
    position: 'absolute',
    bottom: 100,
    right: 24,
    width: 64,
    height: 64,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 5,
  },
  announcementList: {
    gap: 16,
  },
  announcementCard: {
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
  },
  announcementHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  annCategoryBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  annCategoryText: {
    fontSize: 11,
    fontFamily: 'Inter_600SemiBold',
    textTransform: 'uppercase',
  },
  newBadge: {
    paddingHorizontal: 6,
    paddingVertical: 4,
    borderRadius: 4,
  },
  newBadgeText: {
    fontSize: 10,
    fontFamily: 'Inter_700Bold',
  },
  annDate: {
    fontSize: 12,
    fontFamily: 'Inter_400Regular',
  },
  annTitle: {
    fontSize: 16,
    fontFamily: 'Inter_600SemiBold',
    lineHeight: 22,
    marginBottom: 8,
  },
  annContent: {
    fontSize: 14,
    fontFamily: 'Inter_400Regular',
    lineHeight: 20,
  },
});