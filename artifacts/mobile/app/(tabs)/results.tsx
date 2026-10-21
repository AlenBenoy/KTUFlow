import React, { useState } from 'react';
import { StyleSheet, Text, View, ScrollView, RefreshControl, TouchableOpacity, Alert, Modal } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useColors } from '@/hooks/useColors';
import {
  useGetResults,
  useSyncResults,
  getGetResultsQueryKey,
} from '@workspace/api-client-react';
import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import * as Haptics from 'expo-haptics';

export default function ResultsTab() {
  const colors = useColors();
  const queryClient = useQueryClient();
  
  const { data: results, isLoading } = useGetResults();
  const { mutate: syncResults, isPending: isSyncing } = useSyncResults();

  const [expandedSemesters, setExpandedSemesters] = useState<number[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedSemester, setSelectedSemester] = useState(1);
  const [semesterMenuVisible, setSemesterMenuVisible] = useState(false);

  const onRefresh = React.useCallback(() => {
    setRefreshing(true);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    
    syncResults({ data: { semester: selectedSemester } }, {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getGetResultsQueryKey() });
        setRefreshing(false);
      },
      onError: (error) => {
        setRefreshing(false);
        const message = error?.message || 'KTU did not return readable results. No sample data was used.';
        Alert.alert('Results sync failed', message);
      }
    });
  }, [selectedSemester, syncResults, queryClient]);

  const toggleSemester = (sem: number) => {
    Haptics.selectionAsync();
    setExpandedSemesters(prev => 
      prev.includes(sem) ? prev.filter(s => s !== sem) : [...prev, sem]
    );
  };

  const getGradeColor = (grade: string) => {
    if (['S', 'A+', 'O'].includes(grade)) return colors.primary;
    if (['A', 'B+', 'A+', 'A'].includes(grade)) return '#10B981'; // green
    if (['B', 'C+', 'C', 'D'].includes(grade)) return '#F59E0B'; // yellow
    if (['F', 'FE', 'AB'].includes(grade)) return colors.destructive; // red
    return colors.mutedForeground;
  };

  if (isLoading && !results) {
    return (
      <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]} edges={['top']}>
        <View style={styles.centerContainer}>
          <Text style={{ color: colors.mutedForeground }}>Loading results...</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]} edges={['top']}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.foreground }]}>Academic Results</Text>
        <TouchableOpacity 
          style={[styles.syncButton, { backgroundColor: colors.card, borderColor: colors.border }]}
          onPress={onRefresh}
          disabled={isSyncing}
        >
          <Ionicons name="cloud-download-outline" size={20} color={colors.primary} />
        </TouchableOpacity>
      </View>

      <View style={styles.searchBar}>
        <TouchableOpacity
          accessibilityRole="button"
          accessibilityLabel="Select semester"
          style={[styles.semesterPicker, { backgroundColor: colors.card, borderColor: colors.border }]}
          onPress={() => setSemesterMenuVisible(true)}
        >
          <Text style={[styles.pickerLabel, { color: colors.mutedForeground }]}>Semester</Text>
          <Text style={[styles.pickerValue, { color: colors.foreground }]}>S{selectedSemester}</Text>
          <Ionicons name="chevron-down" size={18} color={colors.mutedForeground} />
        </TouchableOpacity>
        <TouchableOpacity
          accessibilityRole="button"
          accessibilityLabel={`Search results for semester ${selectedSemester}`}
          style={[styles.searchButton, { backgroundColor: colors.primary }]}
          onPress={onRefresh}
          disabled={isSyncing}
        >
          <Ionicons name="search" size={18} color={colors.primaryForeground} />
          <Text style={[styles.searchButtonText, { color: colors.primaryForeground }]}>
            {isSyncing ? 'Searching…' : 'Search'}
          </Text>
        </TouchableOpacity>
      </View>

      <Modal
        visible={semesterMenuVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setSemesterMenuVisible(false)}
      >
        <TouchableOpacity
          style={styles.modalBackdrop}
          activeOpacity={1}
          onPress={() => setSemesterMenuVisible(false)}
        >
          <View style={[styles.semesterMenu, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Text style={[styles.menuTitle, { color: colors.foreground }]}>Select semester</Text>
            {[1, 2, 3, 4, 5, 6, 7, 8].map((semester) => (
              <TouchableOpacity
                key={semester}
                style={[styles.menuOption, selectedSemester === semester && { backgroundColor: colors.muted }]}
                onPress={() => {
                  setSelectedSemester(semester);
                  setSemesterMenuVisible(false);
                }}
              >
                <Text style={[styles.menuOptionText, { color: colors.foreground }]}>S{semester}</Text>
                {selectedSemester === semester && (
                  <Ionicons name="checkmark" size={20} color={colors.primary} />
                )}
              </TouchableOpacity>
            ))}
          </View>
        </TouchableOpacity>
      </Modal>

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
        {results?.latestCgpa ? (
          <View style={styles.heroSection}>
            <View style={styles.heroCircle}>
              <View style={[styles.heroCircleInner, { borderColor: colors.primary, backgroundColor: colors.card }]}>
                <Text style={[styles.heroLabel, { color: colors.mutedForeground }]}>CGPA</Text>
                <Text style={[styles.heroValue, { color: colors.primary }]}>{results.latestCgpa.toFixed(2)}</Text>
              </View>
            </View>
          </View>
        ) : null}

        {(!results?.semesters || results.semesters.length === 0) ? (
          <View style={[styles.emptyState, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Ionicons name="document-text-outline" size={48} color={colors.mutedForeground} style={{ marginBottom: 16 }} />
            <Text style={[styles.emptyTitle, { color: colors.foreground }]}>No Results Found</Text>
            <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
              Pull down to sync your latest results from the KTU portal.
            </Text>
            <TouchableOpacity 
              style={[styles.emptyButton, { backgroundColor: colors.primary }]}
              onPress={onRefresh}
            >
              <Text style={[styles.emptyButtonText, { color: colors.primaryForeground }]}>Sync Now</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <View style={styles.semestersList}>
            {results.semesters.map((sem) => {
              const isExpanded = expandedSemesters.includes(sem.semester);
              
              return (
                <View key={sem.semester} style={[styles.semesterCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                  <TouchableOpacity 
                    style={styles.semesterHeader}
                    onPress={() => toggleSemester(sem.semester)}
                  >
                    <View>
                      <Text style={[styles.semesterTitle, { color: colors.foreground }]}>Semester {sem.semester}</Text>
                      {sem.sgpa && <Text style={[styles.semesterSgpa, { color: colors.mutedForeground }]}>SGPA: {sem.sgpa.toFixed(2)}</Text>}
                    </View>
                    <Ionicons 
                      name={isExpanded ? "chevron-up" : "chevron-down"} 
                      size={24} 
                      color={colors.mutedForeground} 
                    />
                  </TouchableOpacity>

                  {isExpanded && (
                    <View style={[styles.subjectsList, { borderTopColor: colors.border }]}>
                      {sem.subjects.map((sub) => (
                        <View key={sub.subjectCode} style={styles.subjectRow}>
                          <View style={styles.subjectInfo}>
                            <Text style={[styles.subjectCode, { color: colors.mutedForeground }]}>{sub.subjectCode}</Text>
                            <Text style={[styles.subjectName, { color: colors.foreground }]} numberOfLines={2}>{sub.subjectName}</Text>
                            {sub.credits !== undefined && (
                              <Text style={[styles.credits, { color: colors.mutedForeground }]}>{sub.credits} Credits</Text>
                            )}
                          </View>
                          <View style={[
                            styles.gradeBadge, 
                            { backgroundColor: getGradeColor(sub.grade) + '20' }
                          ]}>
                            <Text style={[styles.gradeText, { color: getGradeColor(sub.grade) }]}>{sub.grade}</Text>
                          </View>
                        </View>
                      ))}
                    </View>
                  )}
                </View>
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
  searchBar: {
    flexDirection: 'row',
    gap: 10,
    paddingHorizontal: 20,
    paddingBottom: 4,
  },
  semesterPicker: {
    flex: 1,
    minHeight: 52,
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 14,
    justifyContent: 'center',
  },
  pickerLabel: {
    fontSize: 11,
    fontFamily: 'Inter_500Medium',
  },
  pickerValue: {
    fontSize: 16,
    fontFamily: 'Inter_600SemiBold',
    marginTop: 2,
  },
  searchButton: {
    minHeight: 52,
    borderRadius: 12,
    paddingHorizontal: 18,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    justifyContent: 'center',
  },
  searchButtonText: {
    fontSize: 15,
    fontFamily: 'Inter_600SemiBold',
  },
  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.55)',
    justifyContent: 'center',
    padding: 24,
  },
  semesterMenu: {
    borderRadius: 18,
    borderWidth: 1,
    padding: 12,
  },
  menuTitle: {
    fontSize: 18,
    fontFamily: 'Inter_700Bold',
    paddingHorizontal: 10,
    paddingVertical: 10,
  },
  menuOption: {
    minHeight: 46,
    borderRadius: 10,
    paddingHorizontal: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  menuOptionText: {
    fontSize: 16,
    fontFamily: 'Inter_500Medium',
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
    marginVertical: 32,
  },
  heroCircle: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroCircleInner: {
    width: 140,
    height: 140,
    borderRadius: 70,
    borderWidth: 4,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroLabel: {
    fontSize: 16,
    fontFamily: 'Inter_600SemiBold',
    marginBottom: 4,
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  heroValue: {
    fontSize: 40,
    fontFamily: 'Inter_700Bold',
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
    marginBottom: 24,
  },
  emptyButton: {
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  emptyButtonText: {
    fontSize: 16,
    fontFamily: 'Inter_600SemiBold',
  },
  semestersList: {
    gap: 16,
  },
  semesterCard: {
    borderRadius: 16,
    borderWidth: 1,
    overflow: 'hidden',
  },
  semesterHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 20,
  },
  semesterTitle: {
    fontSize: 18,
    fontFamily: 'Inter_600SemiBold',
    marginBottom: 4,
  },
  semesterSgpa: {
    fontSize: 14,
    fontFamily: 'Inter_500Medium',
  },
  subjectsList: {
    borderTopWidth: 1,
  },
  subjectRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: 'rgba(0,0,0,0.05)', // transparent line just in case
  },
  subjectInfo: {
    flex: 1,
    marginRight: 16,
  },
  subjectCode: {
    fontSize: 12,
    fontFamily: 'Inter_600SemiBold',
    marginBottom: 4,
  },
  subjectName: {
    fontSize: 15,
    fontFamily: 'Inter_500Medium',
    lineHeight: 20,
    marginBottom: 4,
  },
  credits: {
    fontSize: 12,
    fontFamily: 'Inter_400Regular',
  },
  gradeBadge: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  gradeText: {
    fontSize: 18,
    fontFamily: 'Inter_700Bold',
  },
});