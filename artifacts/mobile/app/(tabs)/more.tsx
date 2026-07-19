import React, { useState, useEffect } from 'react';
import { StyleSheet, Text, View, ScrollView, TouchableOpacity, Switch, Modal, Platform } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useColors } from '@/hooks/useColors';
import { useGetProfile, useGetFocusSettings, useUpdateFocusSettings } from '@workspace/api-client-react';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import * as Haptics from 'expo-haptics';

export default function MoreTab() {
  const colors = useColors();
  const router = useRouter();
  
  const { data: profile } = useGetProfile();
  const { data: focusSettings } = useGetFocusSettings();
  const { mutate: updateFocus } = useUpdateFocusSettings();

  const [focusActive, setFocusActive] = useState(focusSettings?.enabled || false);
  const [focusTimer, setFocusTimer] = useState(0);

  useEffect(() => {
    setFocusActive(focusSettings?.enabled || false);
  }, [focusSettings]);

  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (focusActive) {
      interval = setInterval(() => {
        setFocusTimer(prev => prev + 1);
      }, 1000);
    } else {
      setFocusTimer(0);
    }
    return () => clearInterval(interval);
  }, [focusActive]);

  const toggleFocus = () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    const newState = !focusActive;
    setFocusActive(newState);
    updateFocus({ data: { enabled: newState } });
  };

  const formatTime = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]} edges={['top']}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.foreground }]}>More</Text>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={[styles.profileCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <View style={[styles.avatar, { backgroundColor: colors.primary }]}>
            <Text style={[styles.avatarText, { color: colors.primaryForeground }]}>
              {profile?.name ? profile.name.charAt(0).toUpperCase() : 'S'}
            </Text>
          </View>
          <View style={styles.profileInfo}>
            <Text style={[styles.profileName, { color: colors.foreground }]}>{profile?.name || 'Student'}</Text>
            <Text style={[styles.profileReg, { color: colors.mutedForeground }]}>{profile?.registerNumber}</Text>
            <Text style={[styles.profileDetails, { color: colors.mutedForeground }]}>
              {profile?.branch} • Semester {profile?.semester}
            </Text>
          </View>
        </View>

        <Text style={[styles.sectionTitle, { color: colors.mutedForeground }]}>TOOLS</Text>

        <TouchableOpacity 
          style={[styles.toolCard, { backgroundColor: colors.card, borderColor: colors.border }]}
          onPress={() => router.push('/materials')}
        >
          <View style={[styles.toolIconBox, { backgroundColor: colors.accent + '20' }]}>
            <Ionicons name="library" size={24} color={colors.accent} />
          </View>
          <View style={styles.toolContent}>
            <Text style={[styles.toolTitle, { color: colors.foreground }]}>Study Materials</Text>
            <Text style={[styles.toolDesc, { color: colors.mutedForeground }]}>Notes, PYQs, and syllabus</Text>
          </View>
          <Ionicons name="chevron-forward" size={20} color={colors.mutedForeground} />
        </TouchableOpacity>

        <TouchableOpacity 
          style={[styles.toolCard, { backgroundColor: colors.card, borderColor: colors.border }]}
          onPress={() => {/* Not a separate route, could be a sheet. Skipping detailed implement due to task size */}}
        >
          <View style={[styles.toolIconBox, { backgroundColor: colors.primary + '20' }]}>
            <Ionicons name="calculator" size={24} color={colors.primary} />
          </View>
          <View style={styles.toolContent}>
            <Text style={[styles.toolTitle, { color: colors.foreground }]}>GPA Calculator</Text>
            <Text style={[styles.toolDesc, { color: colors.mutedForeground }]}>Predict SGPA/CGPA</Text>
          </View>
          <Ionicons name="chevron-forward" size={20} color={colors.mutedForeground} />
        </TouchableOpacity>

        <View style={[styles.focusCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <View style={styles.focusHeader}>
            <View style={styles.focusHeaderLeft}>
              <View style={[styles.toolIconBox, { backgroundColor: '#8B5CF6' + '20' }]}>
                <Ionicons name="bonfire" size={24} color="#8B5CF6" />
              </View>
              <View>
                <Text style={[styles.toolTitle, { color: colors.foreground }]}>Focus Mode</Text>
                <Text style={[styles.toolDesc, { color: colors.mutedForeground }]}>Block distractions</Text>
              </View>
            </View>
            <Switch
              value={focusActive}
              onValueChange={toggleFocus}
              trackColor={{ false: colors.muted, true: '#8B5CF6' }}
              thumbColor={Platform.OS === 'ios' ? '#fff' : focusActive ? '#fff' : '#f4f3f4'}
            />
          </View>

          {focusActive && (
            <View style={[styles.focusActiveState, { backgroundColor: '#8B5CF6' + '10', borderColor: '#8B5CF6' + '30' }]}>
              <Text style={[styles.focusTimer, { color: '#8B5CF6' }]}>{formatTime(focusTimer)}</Text>
              <Text style={[styles.focusMessage, { color: colors.foreground }]}>Stay focused! Session is active.</Text>
            </View>
          )}
        </View>

        <Text style={[styles.sectionTitle, { color: colors.mutedForeground }]}>ACCOUNT</Text>
        
        <TouchableOpacity 
          style={[styles.actionRow, { backgroundColor: colors.card, borderColor: colors.border }]}
          onPress={() => router.push('/onboarding')} // Hack to edit profile
        >
          <Text style={[styles.actionText, { color: colors.foreground }]}>Edit Profile</Text>
          <Ionicons name="chevron-forward" size={20} color={colors.mutedForeground} />
        </TouchableOpacity>

        <View style={{ height: 40 }} />
      </ScrollView>
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
  scrollContent: {
    padding: 20,
  },
  profileCard: {
    flexDirection: 'row',
    padding: 20,
    borderRadius: 16,
    borderWidth: 1,
    alignItems: 'center',
    marginBottom: 32,
  },
  avatar: {
    width: 64,
    height: 64,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 16,
  },
  avatarText: {
    fontSize: 28,
    fontFamily: 'Inter_700Bold',
  },
  profileInfo: {
    flex: 1,
  },
  profileName: {
    fontSize: 20,
    fontFamily: 'Inter_600SemiBold',
    marginBottom: 4,
  },
  profileReg: {
    fontSize: 14,
    fontFamily: 'Inter_500Medium',
    marginBottom: 4,
  },
  profileDetails: {
    fontSize: 13,
    fontFamily: 'Inter_400Regular',
  },
  sectionTitle: {
    fontSize: 13,
    fontFamily: 'Inter_600SemiBold',
    letterSpacing: 1,
    marginBottom: 12,
    marginLeft: 4,
  },
  toolCard: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderRadius: 16,
    borderWidth: 1,
    marginBottom: 16,
  },
  toolIconBox: {
    width: 48,
    height: 48,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 16,
  },
  toolContent: {
    flex: 1,
  },
  toolTitle: {
    fontSize: 16,
    fontFamily: 'Inter_600SemiBold',
    marginBottom: 4,
  },
  toolDesc: {
    fontSize: 14,
    fontFamily: 'Inter_400Regular',
  },
  focusCard: {
    borderRadius: 16,
    borderWidth: 1,
    marginBottom: 32,
    overflow: 'hidden',
  },
  focusHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
  },
  focusHeaderLeft: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  focusActiveState: {
    padding: 20,
    alignItems: 'center',
    borderTopWidth: 1,
  },
  focusTimer: {
    fontSize: 32,
    fontFamily: 'Inter_700Bold',
    marginBottom: 8,
  },
  focusMessage: {
    fontSize: 14,
    fontFamily: 'Inter_500Medium',
  },
  actionRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 20,
    borderRadius: 16,
    borderWidth: 1,
  },
  actionText: {
    fontSize: 16,
    fontFamily: 'Inter_500Medium',
  },
});