import React, { useState } from 'react';
import { StyleSheet, Text, View, TextInput, TouchableOpacity, ScrollView, Platform, KeyboardAvoidingView } from 'react-native';
import { useColors } from '@/hooks/useColors';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { useSaveProfile, useGetProfile } from '@workspace/api-client-react';
import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';

export default function OnboardingScreen() {
  const colors = useColors();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [step, setStep] = useState(1);
  const { mutate: saveProfile, isPending } = useSaveProfile();

  const [form, setForm] = useState({
    name: '',
    registerNumber: '',
    dateOfBirth: '',
    email: '',
    branch: '',
    semester: '1',
    college: '',
    ktuPassword: '',
    ezygoPassword: ''
  });

  const [showKtuPassword, setShowKtuPassword] = useState(false);
  const [showEzygoPassword, setShowEzygoPassword] = useState(false);

  const updateForm = (key: string, value: string) => {
    setForm(prev => ({ ...prev, [key]: value }));
  };

  const nextStep = () => {
    if (step < 4) setStep(step + 1);
  };

  const prevStep = () => {
    if (step > 1) setStep(step - 1);
  };

  const handleSave = () => {
    saveProfile(
      { data: { ...form, semester: parseInt(form.semester) || 1 } },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({ queryKey: ['/api/profile'] });
          router.replace('/(tabs)');
        }
      }
    );
  };

  const renderProgress = () => {
    return (
      <View style={styles.progressContainer}>
        {[1, 2, 3, 4].map((i) => (
          <View
            key={i}
            style={[
              styles.progressDot,
              { backgroundColor: i <= step ? colors.primary : colors.muted },
              i === step && styles.progressDotActive
            ]}
          />
        ))}
      </View>
    );
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
      <KeyboardAvoidingView
        style={styles.keyboardView}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <View style={styles.header}>
          {step > 1 ? (
            <TouchableOpacity onPress={prevStep} style={styles.backButton}>
              <Ionicons name="arrow-back" size={24} color={colors.foreground} />
            </TouchableOpacity>
          ) : <View style={styles.backButtonPlaceholder} />}
          {renderProgress()}
          <View style={styles.backButtonPlaceholder} />
        </View>

        <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
          {step === 1 && (
            <View style={styles.stepContainer}>
              <Text style={[styles.title, { color: colors.foreground }]}>Welcome to KTU Scholar</Text>
              <Text style={[styles.subtitle, { color: colors.mutedForeground }]}>Let's set up your profile for personalized academics.</Text>

              <View style={styles.inputGroup}>
                <Text style={[styles.label, { color: colors.mutedForeground }]}>Full Name</Text>
                <TextInput
                  style={[styles.input, { backgroundColor: colors.input, color: colors.text, borderColor: colors.border }]}
                  value={form.name}
                  onChangeText={(t) => updateForm('name', t)}
                  placeholder="John Doe"
                  placeholderTextColor={colors.mutedForeground}
                />
              </View>

              <View style={styles.inputGroup}>
                <Text style={[styles.label, { color: colors.mutedForeground }]}>Register Number</Text>
                <TextInput
                  style={[styles.input, { backgroundColor: colors.input, color: colors.text, borderColor: colors.border }]}
                  value={form.registerNumber}
                  onChangeText={(t) => updateForm('registerNumber', t.toUpperCase())}
                  placeholder="TVE21CS001"
                  placeholderTextColor={colors.mutedForeground}
                  autoCapitalize="characters"
                />
              </View>

              <View style={styles.inputGroup}>
                <Text style={[styles.label, { color: colors.mutedForeground }]}>Branch</Text>
                <TextInput
                  style={[styles.input, { backgroundColor: colors.input, color: colors.text, borderColor: colors.border }]}
                  value={form.branch}
                  onChangeText={(t) => updateForm('branch', t)}
                  placeholder="Computer Science"
                  placeholderTextColor={colors.mutedForeground}
                />
              </View>

              <View style={styles.row}>
                <View style={[styles.inputGroup, { flex: 1, marginRight: 8 }]}>
                  <Text style={[styles.label, { color: colors.mutedForeground }]}>Semester</Text>
                  <TextInput
                    style={[styles.input, { backgroundColor: colors.input, color: colors.text, borderColor: colors.border }]}
                    value={form.semester}
                    onChangeText={(t) => updateForm('semester', t)}
                    keyboardType="numeric"
                    placeholder="1-8"
                    placeholderTextColor={colors.mutedForeground}
                  />
                </View>
                <View style={[styles.inputGroup, { flex: 1, marginLeft: 8 }]}>
                  <Text style={[styles.label, { color: colors.mutedForeground }]}>DOB</Text>
                  <TextInput
                    style={[styles.input, { backgroundColor: colors.input, color: colors.text, borderColor: colors.border }]}
                    value={form.dateOfBirth}
                    onChangeText={(t) => updateForm('dateOfBirth', t)}
                    placeholder="YYYY-MM-DD"
                    placeholderTextColor={colors.mutedForeground}
                  />
                </View>
              </View>
            </View>
          )}

          {step === 2 && (
            <View style={styles.stepContainer}>
              <Text style={[styles.title, { color: colors.foreground }]}>KTU Portal Credentials</Text>
              <Text style={[styles.subtitle, { color: colors.mutedForeground }]}>We use this to automatically sync your grades and announcements. Credentials are stored securely on your device.</Text>

              <View style={styles.inputGroup}>
                <Text style={[styles.label, { color: colors.mutedForeground }]}>Register Number</Text>
                <TextInput
                  style={[styles.input, { backgroundColor: colors.input, color: colors.text, borderColor: colors.border, opacity: 0.7 }]}
                  value={form.registerNumber}
                  editable={false}
                />
              </View>

              <View style={styles.inputGroup}>
                <Text style={[styles.label, { color: colors.mutedForeground }]}>KTU Password</Text>
                <View style={[styles.passwordContainer, { backgroundColor: colors.input, borderColor: colors.border }]}>
                  <TextInput
                    style={[styles.passwordInput, { color: colors.text }]}
                    value={form.ktuPassword}
                    onChangeText={(t) => updateForm('ktuPassword', t)}
                    placeholder="Enter password"
                    placeholderTextColor={colors.mutedForeground}
                    secureTextEntry={!showKtuPassword}
                  />
                  <TouchableOpacity onPress={() => setShowKtuPassword(!showKtuPassword)} style={styles.eyeButton}>
                    <Ionicons name={showKtuPassword ? "eye-off" : "eye"} size={20} color={colors.mutedForeground} />
                  </TouchableOpacity>
                </View>
              </View>
            </View>
          )}

          {step === 3 && (
            <View style={styles.stepContainer}>
              <Text style={[styles.title, { color: colors.foreground }]}>EzyGo Credentials</Text>
              <Text style={[styles.subtitle, { color: colors.mutedForeground }]}>EzyGo is the standard attendance tracking portal for most KTU colleges. Link it to track classes, cuts, and condonation status.</Text>

              <View style={styles.inputGroup}>
                <Text style={[styles.label, { color: colors.mutedForeground }]}>EzyGo Password</Text>
                <View style={[styles.passwordContainer, { backgroundColor: colors.input, borderColor: colors.border }]}>
                  <TextInput
                    style={[styles.passwordInput, { color: colors.text }]}
                    value={form.ezygoPassword}
                    onChangeText={(t) => updateForm('ezygoPassword', t)}
                    placeholder="Enter EzyGo password"
                    placeholderTextColor={colors.mutedForeground}
                    secureTextEntry={!showEzygoPassword}
                  />
                  <TouchableOpacity onPress={() => setShowEzygoPassword(!showEzygoPassword)} style={styles.eyeButton}>
                    <Ionicons name={showEzygoPassword ? "eye-off" : "eye"} size={20} color={colors.mutedForeground} />
                  </TouchableOpacity>
                </View>
              </View>
            </View>
          )}

          {step === 4 && (
            <View style={styles.stepContainer}>
              <Text style={[styles.title, { color: colors.foreground }]}>Review & Save</Text>
              <Text style={[styles.subtitle, { color: colors.mutedForeground }]}>Almost there. Please verify your details before starting.</Text>

              <View style={[styles.summaryCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <View style={styles.summaryRow}>
                  <Text style={[styles.summaryLabel, { color: colors.mutedForeground }]}>Name</Text>
                  <Text style={[styles.summaryValue, { color: colors.foreground }]}>{form.name || '-'}</Text>
                </View>
                <View style={styles.summaryRow}>
                  <Text style={[styles.summaryLabel, { color: colors.mutedForeground }]}>Reg. No</Text>
                  <Text style={[styles.summaryValue, { color: colors.foreground }]}>{form.registerNumber || '-'}</Text>
                </View>
                <View style={styles.summaryRow}>
                  <Text style={[styles.summaryLabel, { color: colors.mutedForeground }]}>Branch</Text>
                  <Text style={[styles.summaryValue, { color: colors.foreground }]}>{form.branch || '-'}</Text>
                </View>
                <View style={styles.summaryRow}>
                  <Text style={[styles.summaryLabel, { color: colors.mutedForeground }]}>Semester</Text>
                  <Text style={[styles.summaryValue, { color: colors.foreground }]}>{form.semester || '-'}</Text>
                </View>
                
                <View style={[styles.divider, { backgroundColor: colors.border }]} />
                
                <View style={styles.summaryRow}>
                  <Text style={[styles.summaryLabel, { color: colors.mutedForeground }]}>KTU Sync</Text>
                  <Text style={[styles.summaryValue, { color: form.ktuPassword ? colors.primary : colors.mutedForeground }]}>
                    {form.ktuPassword ? 'Enabled' : 'Disabled'}
                  </Text>
                </View>
                <View style={styles.summaryRow}>
                  <Text style={[styles.summaryLabel, { color: colors.mutedForeground }]}>EzyGo Sync</Text>
                  <Text style={[styles.summaryValue, { color: form.ezygoPassword ? colors.primary : colors.mutedForeground }]}>
                    {form.ezygoPassword ? 'Enabled' : 'Disabled'}
                  </Text>
                </View>
              </View>
            </View>
          )}
        </ScrollView>

        <View style={[styles.footer, { borderTopColor: colors.border }]}>
          <TouchableOpacity
            style={[
              styles.primaryButton,
              { backgroundColor: colors.primary },
              (!form.registerNumber && step === 1) && { opacity: 0.5 }
            ]}
            onPress={step === 4 ? handleSave : nextStep}
            disabled={(!form.registerNumber && step === 1) || isPending}
          >
            <Text style={[styles.primaryButtonText, { color: colors.primaryForeground }]}>
              {isPending ? 'Saving...' : step === 4 ? 'Get Started' : 'Continue'}
            </Text>
            {!isPending && <Ionicons name={step === 4 ? "checkmark" : "arrow-forward"} size={20} color={colors.primaryForeground} style={styles.buttonIcon} />}
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  keyboardView: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  backButton: {
    padding: 8,
  },
  backButtonPlaceholder: {
    width: 40,
  },
  progressContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  progressDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  progressDotActive: {
    width: 24,
  },
  content: {
    flex: 1,
  },
  contentContainer: {
    padding: 24,
  },
  stepContainer: {
    flex: 1,
  },
  title: {
    fontSize: 28,
    fontFamily: 'Inter_700Bold',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    fontFamily: 'Inter_400Regular',
    marginBottom: 32,
    lineHeight: 24,
  },
  inputGroup: {
    marginBottom: 20,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  label: {
    fontSize: 14,
    fontFamily: 'Inter_500Medium',
    marginBottom: 8,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  input: {
    height: 52,
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 16,
    fontSize: 16,
    fontFamily: 'Inter_400Regular',
  },
  passwordContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 52,
    borderWidth: 1,
    borderRadius: 12,
  },
  passwordInput: {
    flex: 1,
    height: '100%',
    paddingHorizontal: 16,
    fontSize: 16,
    fontFamily: 'Inter_400Regular',
  },
  eyeButton: {
    padding: 14,
  },
  summaryCard: {
    borderWidth: 1,
    borderRadius: 16,
    padding: 20,
  },
  summaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
  },
  summaryLabel: {
    fontSize: 15,
    fontFamily: 'Inter_400Regular',
  },
  summaryValue: {
    fontSize: 15,
    fontFamily: 'Inter_600SemiBold',
  },
  divider: {
    height: 1,
    marginVertical: 12,
  },
  footer: {
    padding: 24,
    paddingBottom: Platform.OS === 'ios' ? 0 : 24,
    borderTopWidth: 1,
  },
  primaryButton: {
    height: 56,
    borderRadius: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryButtonText: {
    fontSize: 16,
    fontFamily: 'Inter_600SemiBold',
  },
  buttonIcon: {
    marginLeft: 8,
  },
});