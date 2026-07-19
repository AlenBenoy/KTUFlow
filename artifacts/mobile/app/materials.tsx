import React, { useState } from 'react';
import { StyleSheet, Text, View, ScrollView, TouchableOpacity, Linking } from 'react-native';
import { useColors } from '@/hooks/useColors';
import { useGetMaterials } from '@workspace/api-client-react';
import { Ionicons } from '@expo/vector-icons';
import * as WebBrowser from 'expo-web-browser';
import * as Haptics from 'expo-haptics';

export default function MaterialsScreen() {
  const colors = useColors();
  
  const [semesterFilter, setSemesterFilter] = useState<number | undefined>(undefined);
  const [typeFilter, setTypeFilter] = useState<'all' | 'notes' | 'pyq'>('all');

  const { data: materialsData, isLoading } = useGetMaterials({ semester: semesterFilter, type: typeFilter });

  const openUrl = async (url: string) => {
    Haptics.selectionAsync();
    await WebBrowser.openBrowserAsync(url, {
      presentationStyle: WebBrowser.WebBrowserPresentationStyle.PAGE_SHEET,
      toolbarColor: colors.background,
      controlsColor: colors.primary,
    });
  };

  const semesters = [1, 2, 3, 4, 5, 6, 7, 8];

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={[styles.sourceBanners, { borderBottomColor: colors.border }]}>
        <TouchableOpacity 
          style={[styles.banner, { backgroundColor: colors.primary + '15', borderColor: colors.primary + '30' }]}
          onPress={() => openUrl('https://ktunotes.in')}
        >
          <Ionicons name="globe-outline" size={16} color={colors.primary} />
          <Text style={[styles.bannerText, { color: colors.primary }]}>ktunotes.in</Text>
        </TouchableOpacity>
        <TouchableOpacity 
          style={[styles.banner, { backgroundColor: colors.accent + '15', borderColor: colors.accent + '30' }]}
          onPress={() => openUrl('https://ktuspecial.in')}
        >
          <Ionicons name="globe-outline" size={16} color={colors.accent} />
          <Text style={[styles.bannerText, { color: colors.accent }]}>ktuspecial.in</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.filtersContainer}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterScroll}>
          <TouchableOpacity 
            style={[
              styles.filterChip, 
              { backgroundColor: semesterFilter === undefined ? colors.primary : colors.muted },
              semesterFilter === undefined && { borderColor: colors.primary, borderWidth: 1 }
            ]}
            onPress={() => { Haptics.selectionAsync(); setSemesterFilter(undefined); }}
          >
            <Text style={[styles.filterText, { color: semesterFilter === undefined ? colors.primaryForeground : colors.mutedForeground }]}>All Sems</Text>
          </TouchableOpacity>
          {semesters.map(sem => (
            <TouchableOpacity 
              key={sem}
              style={[
                styles.filterChip, 
                { backgroundColor: semesterFilter === sem ? colors.primary : colors.muted },
                semesterFilter === sem && { borderColor: colors.primary, borderWidth: 1 }
              ]}
              onPress={() => { Haptics.selectionAsync(); setSemesterFilter(sem); }}
            >
              <Text style={[styles.filterText, { color: semesterFilter === sem ? colors.primaryForeground : colors.mutedForeground }]}>S{sem}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        <View style={styles.typeFilters}>
          {['all', 'notes', 'pyq'].map(type => (
            <TouchableOpacity 
              key={type}
              style={[
                styles.typeChip, 
                { backgroundColor: typeFilter === type ? colors.card : 'transparent' },
                typeFilter === type && { shadowColor: '#000', shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.1, shadowRadius: 2 }
              ]}
              onPress={() => { Haptics.selectionAsync(); setTypeFilter(type as any); }}
            >
              <Text style={[styles.typeText, { color: typeFilter === type ? colors.foreground : colors.mutedForeground }]}>
                {type === 'pyq' ? 'Question Papers' : type.charAt(0).toUpperCase() + type.slice(1)}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      <ScrollView contentContainerStyle={styles.listContent}>
        {isLoading ? (
          <View style={styles.centerContainer}>
            <Text style={{ color: colors.mutedForeground }}>Loading materials...</Text>
          </View>
        ) : (!materialsData?.materials || materialsData.materials.length === 0) ? (
          <View style={[styles.emptyState, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Ionicons name="document-text-outline" size={48} color={colors.mutedForeground} style={{ marginBottom: 16 }} />
            <Text style={[styles.emptyTitle, { color: colors.foreground }]}>No Materials Found</Text>
            <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
              Try changing the filters to see more results.
            </Text>
          </View>
        ) : (
          materialsData.materials.map(item => (
            <TouchableOpacity 
              key={item.id} 
              style={[styles.materialCard, { backgroundColor: colors.card, borderColor: colors.border }]}
              onPress={() => openUrl(item.url)}
            >
              <View style={styles.materialHeader}>
                <View style={[styles.typeBadge, { backgroundColor: item.type === 'notes' ? colors.primary + '20' : colors.accent + '20' }]}>
                  <Text style={[styles.typeBadgeText, { color: item.type === 'notes' ? colors.primary : colors.accent }]}>
                    {item.type.toUpperCase()}
                  </Text>
                </View>
                <View style={[styles.sourceBadge, { backgroundColor: colors.muted }]}>
                  <Text style={[styles.sourceBadgeText, { color: colors.mutedForeground }]}>{item.source}</Text>
                </View>
              </View>
              <Text style={[styles.materialTitle, { color: colors.foreground }]} numberOfLines={2}>{item.title}</Text>
              {item.subjectCode && (
                <View style={styles.materialMeta}>
                  <Text style={[styles.metaText, { color: colors.mutedForeground }]}>{item.subjectCode}</Text>
                  {item.semester && <Text style={[styles.metaText, { color: colors.mutedForeground }]}> • Sem {item.semester}</Text>}
                </View>
              )}
            </TouchableOpacity>
          ))
        )}
        <View style={{ height: 40 }} />
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centerContainer: {
    padding: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sourceBanners: {
    flexDirection: 'row',
    padding: 16,
    gap: 12,
    borderBottomWidth: 1,
  },
  banner: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 10,
    borderRadius: 8,
    borderWidth: 1,
    gap: 6,
  },
  bannerText: {
    fontSize: 14,
    fontFamily: 'Inter_600SemiBold',
  },
  filtersContainer: {
    paddingVertical: 16,
  },
  filterScroll: {
    paddingHorizontal: 16,
    gap: 8,
  },
  filterChip: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
  },
  filterText: {
    fontSize: 14,
    fontFamily: 'Inter_500Medium',
  },
  typeFilters: {
    flexDirection: 'row',
    marginHorizontal: 16,
    marginTop: 16,
    backgroundColor: 'rgba(0,0,0,0.05)',
    borderRadius: 8,
    padding: 4,
  },
  typeChip: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 8,
    borderRadius: 6,
  },
  typeText: {
    fontSize: 13,
    fontFamily: 'Inter_600SemiBold',
  },
  listContent: {
    padding: 16,
    gap: 12,
  },
  emptyState: {
    padding: 32,
    borderRadius: 16,
    borderWidth: 1,
    alignItems: 'center',
    marginTop: 16,
  },
  emptyTitle: {
    fontSize: 18,
    fontFamily: 'Inter_600SemiBold',
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 14,
    fontFamily: 'Inter_400Regular',
    textAlign: 'center',
    lineHeight: 20,
  },
  materialCard: {
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
  },
  materialHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  typeBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  typeBadgeText: {
    fontSize: 10,
    fontFamily: 'Inter_700Bold',
  },
  sourceBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  sourceBadgeText: {
    fontSize: 10,
    fontFamily: 'Inter_600SemiBold',
  },
  materialTitle: {
    fontSize: 16,
    fontFamily: 'Inter_500Medium',
    lineHeight: 22,
    marginBottom: 8,
  },
  materialMeta: {
    flexDirection: 'row',
  },
  metaText: {
    fontSize: 13,
    fontFamily: 'Inter_400Regular',
  },
});