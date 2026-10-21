import React, { useState } from 'react';
import { StyleSheet, Text, View, ScrollView, RefreshControl, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useColors } from '@/hooks/useColors';
import {
  useGetProfile,
  useGetGpaSummary,
  useGetAttendance,
  useGetTodos,
  useGetAnnouncements,
  useSyncResults,
  useSyncAttendance,
  getGetProfileQueryKey,
  getGetGpaSummaryQueryKey,
  getGetAttendanceQueryKey,
  getGetTodosQueryKey,
  getGetAnnouncementsQueryKey,
} from '@workspace/api-client-react';
import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import * as Haptics from 'expo-haptics';
import { useRouter } from 'expo-router';
import Animated, { useAnimatedStyle, withTiming, withSpring } from 'react-native-reanimated';

function CircularProgress({ percentage, color, backgroundColor }: { percentage: number, color: string, backgroundColor: string }) {
  // Simple textual implementation for Home
  return (
    <View style={[styles.circularProgressContainer, { borderColor: backgroundColor }]}>
      <Text style={[styles.circularProgressText, { color }]}>{percentage.toFixed(1)}%</Text>
    </View>
  );
}

export default function HomeTab() {
  const colors = useColors();
  const queryClient = useQueryClient();
  const router = useRouter();
  
  const { data: profile } = useGetProfile();
  const { data: gpaSummary } = useGetGpaSummary();
  const { data: attendance } = useGetAttendance();
  const { data: todosData } = useGetTodos({ status: 'pending' });
  const { data: announcementsData } = useGetAnnouncements({ limit: 3 });

  const { mutate: syncResults, isPending: isSyncingResults } = useSyncResults();
  const { mutate: syncAttendance, isPending: isSyncingAttendance } = useSyncAttendance();

  const [refreshing, setRefreshing] = useState(false);

  const onRefresh = React.useCallback(() => {
    setRefreshing(true);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    
    Promise.all([
      new Promise(resolve => syncResults({ data: { semester: profile?.semester ?? 1 } }, { onSettled: resolve })),
      new Promise(resolve => syncAttendance(undefined, { onSettled: resolve }))
    ]).then(() => {
      queryClient.invalidateQueries({ queryKey: getGetProfileQueryKey() });
      queryClient.invalidateQueries({ queryKey: getGetGpaSummaryQueryKey() });
      queryClient.invalidateQueries({ queryKey: getGetAttendanceQueryKey() });
      setRefreshing(false);
    });
  }, [profile?.semester, syncResults, syncAttendance, queryClient]);

  const todos = todosData?.todos?.slice(0, 3) || [];
  const announcements = announcementsData?.announcements?.slice(0, 3) || [];

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]} edges={['top']}>
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
        <View style={styles.header}>
          <View>
            <Text style={[styles.greeting, { color: colors.mutedForeground }]}>Welcome back,</Text>
            <Text style={[styles.name, { color: colors.foreground }]}>{profile?.name || 'Scholar'}</Text>
            <Text style={[styles.regNumber, { color: colors.mutedForeground }]}>{profile?.registerNumber}</Text>
          </View>
          <TouchableOpacity 
            style={[styles.syncButton, { backgroundColor: colors.card, borderColor: colors.border }]}
            onPress={onRefresh}
            disabled={isSyncingResults || isSyncingAttendance}
          >
            <Ionicons name="sync" size={20} color={colors.primary} />
          </TouchableOpacity>
        </View>

        <View style={styles.statsRow}>
          <TouchableOpacity 
            style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}
            onPress={() => router.push('/(tabs)/results')}
          >
            <View style={styles.statHeader}>
              <Ionicons name="school" size={20} color={colors.primary} />
              <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>CGPA</Text>
            </View>
            <Text style={[styles.statValue, { color: colors.foreground }]}>
              {gpaSummary?.cgpa ? gpaSummary.cgpa.toFixed(2) : '-.--'}
            </Text>
            <Text style={[styles.statSub, { color: colors.mutedForeground }]}>
              {gpaSummary?.classification || 'No results yet'}
            </Text>
          </TouchableOpacity>

          <TouchableOpacity 
            style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}
            onPress={() => router.push('/(tabs)/attendance')}
          >
            <View style={styles.statHeader}>
              <Ionicons name="pie-chart" size={20} color={colors.primary} />
              <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>Attendance</Text>
            </View>
            <View style={styles.attendanceRow}>
              <Text style={[styles.statValue, { color: colors.foreground }]}>
                {attendance?.overallPercentage ? attendance.overallPercentage.toFixed(1) + '%' : '--%'}
              </Text>
            </View>
            <Text style={[
              styles.statSub, 
              { color: (attendance?.overallPercentage && attendance.overallPercentage < 75) ? colors.destructive : colors.mutedForeground }
            ]}>
              {(attendance?.overallPercentage && attendance.overallPercentage < 75) ? 'Below requirement' : 'Safe zone'}
            </Text>
          </TouchableOpacity>
        </View>

        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Text style={[styles.sectionTitle, { color: colors.foreground }]}>Upcoming Tasks</Text>
            <TouchableOpacity onPress={() => router.push('/(tabs)/tasks')}>
              <Text style={[styles.seeAll, { color: colors.primary }]}>See all</Text>
            </TouchableOpacity>
          </View>

          {todos.length === 0 ? (
            <View style={[styles.emptyCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <Ionicons name="checkmark-done-circle-outline" size={32} color={colors.mutedForeground} />
              <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>All caught up!</Text>
            </View>
          ) : (
            todos.map(todo => (
              <View key={todo.id} style={[styles.todoCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <View style={[
                  styles.priorityIndicator, 
                  { backgroundColor: todo.priority === 'high' ? colors.destructive : todo.priority === 'medium' ? colors.primary : colors.accent }
                ]} />
                <View style={styles.todoContent}>
                  <Text style={[styles.todoTitle, { color: colors.foreground }]}>{todo.title}</Text>
                  {todo.dueDate && (
                    <View style={styles.todoMeta}>
                      <Ionicons name="calendar-outline" size={12} color={colors.mutedForeground} />
                      <Text style={[styles.todoDate, { color: colors.mutedForeground }]}>{new Date(todo.dueDate).toLocaleDateString()}</Text>
                    </View>
                  )}
                </View>
              </View>
            ))
          )}
        </View>

        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Text style={[styles.sectionTitle, { color: colors.foreground }]}>Recent Announcements</Text>
          </View>

          {announcements.length === 0 ? (
            <View style={[styles.emptyCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <Ionicons name="notifications-off-outline" size={32} color={colors.mutedForeground} />
              <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>No new announcements</Text>
            </View>
          ) : (
            announcements.map(ann => (
              <View key={ann.id} style={[styles.announcementCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <View style={styles.announcementHeader}>
                  <View style={[styles.categoryChip, { backgroundColor: colors.muted }]}>
                    <Text style={[styles.categoryText, { color: colors.mutedForeground }]}>{ann.category}</Text>
                  </View>
                  <Text style={[styles.announcementDate, { color: colors.mutedForeground }]}>{new Date(ann.date).toLocaleDateString()}</Text>
                </View>
                <Text style={[styles.announcementTitle, { color: colors.foreground }]} numberOfLines={2}>
                  {ann.title}
                </Text>
              </View>
            ))
          )}
        </View>
        
        <View style={{ height: 100 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    padding: 20,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 32,
  },
  greeting: {
    fontSize: 14,
    fontFamily: 'Inter_500Medium',
    marginBottom: 4,
  },
  name: {
    fontSize: 28,
    fontFamily: 'Inter_700Bold',
    letterSpacing: -0.5,
  },
  regNumber: {
    fontSize: 14,
    fontFamily: 'Inter_500Medium',
    marginTop: 4,
  },
  syncButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  statsRow: {
    flexDirection: 'row',
    gap: 16,
    marginBottom: 32,
  },
  statCard: {
    flex: 1,
    padding: 20,
    borderRadius: 16,
    borderWidth: 1,
  },
  statHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 16,
  },
  statLabel: {
    fontSize: 14,
    fontFamily: 'Inter_600SemiBold',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  statValue: {
    fontSize: 32,
    fontFamily: 'Inter_700Bold',
  },
  statSub: {
    fontSize: 13,
    fontFamily: 'Inter_400Regular',
    marginTop: 4,
  },
  attendanceRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  circularProgressContainer: {
    width: 60,
    height: 60,
    borderRadius: 30,
    borderWidth: 4,
    alignItems: 'center',
    justifyContent: 'center',
  },
  circularProgressText: {
    fontSize: 14,
    fontFamily: 'Inter_700Bold',
  },
  section: {
    marginBottom: 32,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 18,
    fontFamily: 'Inter_600SemiBold',
  },
  seeAll: {
    fontSize: 14,
    fontFamily: 'Inter_500Medium',
  },
  todoCard: {
    flexDirection: 'row',
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 12,
    overflow: 'hidden',
  },
  priorityIndicator: {
    width: 4,
  },
  todoContent: {
    flex: 1,
    padding: 16,
  },
  todoTitle: {
    fontSize: 16,
    fontFamily: 'Inter_500Medium',
    marginBottom: 8,
  },
  todoMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  todoDate: {
    fontSize: 13,
    fontFamily: 'Inter_400Regular',
  },
  emptyCard: {
    padding: 32,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    borderStyle: 'dashed',
  },
  emptyText: {
    fontSize: 14,
    fontFamily: 'Inter_500Medium',
    marginTop: 12,
  },
  announcementCard: {
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 12,
  },
  announcementHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  categoryChip: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  categoryText: {
    fontSize: 11,
    fontFamily: 'Inter_600SemiBold',
    textTransform: 'uppercase',
  },
  announcementDate: {
    fontSize: 12,
    fontFamily: 'Inter_400Regular',
  },
  announcementTitle: {
    fontSize: 15,
    fontFamily: 'Inter_500Medium',
    lineHeight: 22,
  },
});