import React, { useState } from 'react';
import { StyleSheet, Text, View, ScrollView, RefreshControl, TouchableOpacity, Dimensions } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useColors } from '@/hooks/useColors';
import {
  useGetAttendance,
  useGetAttendanceAnalysis,
  useSyncAttendance,
  getGetAttendanceQueryKey,
  getGetAttendanceAnalysisQueryKey,
} from '@workspace/api-client-react';
import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import * as Haptics from 'expo-haptics';
import Animated, { useAnimatedStyle, withTiming } from 'react-native-reanimated';

const { width } = Dimensions.get('window');

function ProgressBar({ percentage, color, backgroundColor }: { percentage: number, color: string, backgroundColor: string }) {
  const animatedStyle = useAnimatedStyle(() => {
    return {
      width: withTiming(`${Math.min(100, Math.max(0, percentage))}%`, { duration: 1000 }),
    };
  });

  return (
    <View style={[styles.progressTrack, { backgroundColor }]}>
      <Animated.View style={[styles.progressFill, { backgroundColor: color }, animatedStyle]} />
    </View>
  );
}

export default function AttendanceTab() {
  const colors = useColors();
  const queryClient = useQueryClient();
  
  const { data: attendance, isLoading } = useGetAttendance();
  const { data: analysis } = useGetAttendanceAnalysis();
  const { mutate: syncAttendance, isPending: isSyncing } = useSyncAttendance();

  const [refreshing, setRefreshing] = useState(false);
  const [selectedSubject, setSelectedSubject] = useState<string | null>(null);

  const onRefresh = React.useCallback(() => {
    setRefreshing(true);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    
    syncAttendance(undefined, {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getGetAttendanceQueryKey() });
        queryClient.invalidateQueries({ queryKey: getGetAttendanceAnalysisQueryKey() });
        setRefreshing(false);
      },
      onError: () => {
        setRefreshing(false);
      }
    });
  }, [syncAttendance, queryClient]);

  const getStatusColor = (status: string) => {
    switch(status) {
      case 'safe': return '#10B981';
      case 'warning': return '#F59E0B';
      case 'danger': return colors.destructive;
      case 'condonation': return '#8B5CF6';
      default: return colors.mutedForeground;
    }
  };

  const overallPercentage = attendance?.overallPercentage || 0;
  const overallColor = overallPercentage >= 75 ? '#10B981' : overallPercentage >= 65 ? '#F59E0B' : colors.destructive;

  if (isLoading && !attendance) {
    return (
      <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]} edges={['top']}>
        <View style={styles.centerContainer}>
          <Text style={{ color: colors.mutedForeground }}>Loading attendance...</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]} edges={['top']}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.foreground }]}>Attendance</Text>
        <TouchableOpacity 
          style={[styles.syncButton, { backgroundColor: colors.card, borderColor: colors.border }]}
          onPress={onRefresh}
          disabled={isSyncing}
        >
          <Ionicons name="refresh" size={20} color={colors.primary} />
        </TouchableOpacity>
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
        {attendance ? (
          <View style={styles.heroSection}>
            <View style={styles.heroCircle}>
              <View style={[styles.heroCircleInner, { borderColor: overallColor, backgroundColor: colors.card }]}>
                <Text style={[styles.heroValue, { color: colors.foreground }]}>{overallPercentage.toFixed(1)}%</Text>
                <Text style={[styles.heroLabel, { color: colors.mutedForeground }]}>OVERALL</Text>
              </View>
            </View>
            
            {analysis?.summary && (
              <View style={[styles.summaryCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Ionicons name="information-circle" size={20} color={colors.primary} style={{ marginTop: 2 }} />
                <Text style={[styles.summaryText, { color: colors.foreground }]}>{analysis.summary}</Text>
              </View>
            )}
          </View>
        ) : null}

        {(!attendance?.subjects || attendance.subjects.length === 0) ? (
          <View style={[styles.emptyState, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Ionicons name="calendar-outline" size={48} color={colors.mutedForeground} style={{ marginBottom: 16 }} />
            <Text style={[styles.emptyTitle, { color: colors.foreground }]}>No Attendance Data</Text>
            <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
              Sync from EzyGo to view your attendance.
            </Text>
          </View>
        ) : (
          <View style={styles.subjectsList}>
            {attendance.subjects.map((sub) => {
              const statusColor = getStatusColor(sub.status);
              const recommendation = analysis?.subjects.find(s => s.subjectCode === sub.subjectCode);
              const isExpanded = selectedSubject === sub.subjectCode;
              
              return (
                <TouchableOpacity 
                  key={sub.subjectCode} 
                  style={[styles.subjectCard, { backgroundColor: colors.card, borderColor: colors.border }]}
                  onPress={() => {
                    Haptics.selectionAsync();
                    setSelectedSubject(isExpanded ? null : sub.subjectCode);
                  }}
                  activeOpacity={0.8}
                >
                  <View style={styles.subjectHeader}>
                    <View style={styles.subjectInfo}>
                      <Text style={[styles.subjectName, { color: colors.foreground }]} numberOfLines={2}>{sub.subjectName}</Text>
                      <Text style={[styles.subjectCode, { color: colors.mutedForeground }]}>{sub.subjectCode}</Text>
                    </View>
                    <View style={styles.percentageContainer}>
                      <Text style={[styles.percentageValue, { color: statusColor }]}>{sub.percentage.toFixed(1)}%</Text>
                      <Text style={[styles.attendanceFraction, { color: colors.mutedForeground }]}>{sub.attendedClasses}/{sub.totalClasses}</Text>
                    </View>
                  </View>

                  <ProgressBar 
                    percentage={sub.percentage} 
                    color={statusColor} 
                    backgroundColor={colors.muted} 
                  />

                  {isExpanded && recommendation && (
                    <View style={[styles.recommendationBox, { backgroundColor: colors.muted }]}>
                      <Text style={[styles.recommendationText, { color: colors.foreground }]}>{recommendation.recommendation}</Text>
                      
                      <View style={styles.recommendationStats}>
                        {sub.classesNeeded !== undefined && sub.classesNeeded > 0 && (
                          <View style={styles.recStatItem}>
                            <Text style={[styles.recStatValue, { color: colors.primary }]}>{sub.classesNeeded}</Text>
                            <Text style={[styles.recStatLabel, { color: colors.mutedForeground }]}>Need to attend</Text>
                          </View>
                        )}
                        {sub.classesCanSkip !== undefined && sub.classesCanSkip > 0 && (
                          <View style={styles.recStatItem}>
                            <Text style={[styles.recStatValue, { color: '#10B981' }]}>{sub.classesCanSkip}</Text>
                            <Text style={[styles.recStatLabel, { color: colors.mutedForeground }]}>Can skip</Text>
                          </View>
                        )}
                      </View>
                    </View>
                  )}
                </TouchableOpacity>
              );
            })}
          </View>
        )}
        
        <View style={{ height: 100 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centerContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 8,
  },
  title: {
    fontSize: 28,
    fontFamily: 'Inter_700Bold',
  },
  syncButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  scrollContent: {
    padding: 20,
  },
  heroSection: {
    alignItems: 'center',
    marginBottom: 32,
  },
  heroCircle: {
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 24,
    marginTop: 16,
  },
  heroCircleInner: {
    width: 160,
    height: 160,
    borderRadius: 80,
    borderWidth: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroValue: {
    fontSize: 40,
    fontFamily: 'Inter_700Bold',
  },
  heroLabel: {
    fontSize: 14,
    fontFamily: 'Inter_600SemiBold',
    letterSpacing: 1,
    marginTop: 4,
  },
  summaryCard: {
    flexDirection: 'row',
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    gap: 12,
    width: '100%',
  },
  summaryText: {
    flex: 1,
    fontSize: 14,
    fontFamily: 'Inter_500Medium',
    lineHeight: 20,
  },
  emptyState: {
    padding: 32,
    borderRadius: 16,
    borderWidth: 1,
    alignItems: 'center',
    marginTop: 32,
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
  subjectsList: {
    gap: 16,
  },
  subjectCard: {
    padding: 20,
    borderRadius: 16,
    borderWidth: 1,
  },
  subjectHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 16,
  },
  subjectInfo: {
    flex: 1,
    paddingRight: 16,
  },
  subjectName: {
    fontSize: 16,
    fontFamily: 'Inter_600SemiBold',
    marginBottom: 4,
  },
  subjectCode: {
    fontSize: 13,
    fontFamily: 'Inter_500Medium',
  },
  percentageContainer: {
    alignItems: 'flex-end',
  },
  percentageValue: {
    fontSize: 20,
    fontFamily: 'Inter_700Bold',
  },
  attendanceFraction: {
    fontSize: 12,
    fontFamily: 'Inter_500Medium',
    marginTop: 2,
  },
  progressTrack: {
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 4,
  },
  recommendationBox: {
    marginTop: 16,
    padding: 16,
    borderRadius: 12,
  },
  recommendationText: {
    fontSize: 14,
    fontFamily: 'Inter_500Medium',
    lineHeight: 20,
    marginBottom: 16,
  },
  recommendationStats: {
    flexDirection: 'row',
    gap: 24,
  },
  recStatItem: {
    alignItems: 'center',
  },
  recStatValue: {
    fontSize: 24,
    fontFamily: 'Inter_700Bold',
    marginBottom: 2,
  },
  recStatLabel: {
    fontSize: 12,
    fontFamily: 'Inter_500Medium',
  },
});