const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_lightup_123";
const BOB_UID = "bob_lightup_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, ts) {
  return {
    userId: uid,
    userName: "Alex",
    goalMode: "GAIN",
    surplusPace: "LEAN_GAIN",
    currentWeightKg: 66.5,
    targetWeightKg: 72.0,
    heightCm: 176,
    age: 26,
    activityLevel: "MODERATE",
    dailyCalorieTarget: 2850,
    proteinTargetG: 160,
    carbsTargetG: 355,
    fatTargetG: 85,
    waterTargetMl: 2750,
    useMetricUnits: true,
    dietaryTags: ["High-Protein"],
    darkTheme: false,
    mealRemindersEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any LightUp collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, ts)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("mealLogs").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("mealLogs").doc("meal_1").set({
      userId: ALICE_UID,
      dateKey: "2026-11-19",
      mealSlot: "LUNCH",
      name: "Salmon Rice Bowl",
      calories: 720,
      proteinGrams: 46,
      carbsGrams: 78,
      fatGrams: 24,
      completed: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("UserProfile create, action-scoped updates, and shadow-field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validProfilePayload(ALICE_UID, created)));

  // Partial action update: settings / dietary tags
  await assertSucceeds(
    profileRef.update({
      useMetricUnits: false,
      dietaryTags: ["High-Protein", "Dairy-Free"],
      darkTheme: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Partial action update: currentWeightKg check-in sync
  await assertSucceeds(
    profileRef.update({
      currentWeightKg: 67.1,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field ("shadow update")
  await assertFails(
    profileRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject immutable createdAt overwrite
  await assertFails(
    profileRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject future timestamp tampering
  await assertFails(
    profileRef.update({
      currentWeightKg: 67.2,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Cross-user isolation blocks Bob from Alice profile and subcollections", async () => {
  const ts = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, ts));
    await adminDb.collection("users").doc(ALICE_UID).collection("mealLogs").doc("meal_1").set({
      userId: ALICE_UID,
      dateKey: "2026-11-19",
      mealSlot: "BREAKFAST",
      name: "Oat Shake",
      calories: 650,
      proteinGrams: 38,
      carbsGrams: 72,
      fatGrams: 22,
      completed: true,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("mealLogs").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("mealLogs").doc("meal_1").update({
      completed: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("mealLogs").doc("meal_1").delete());
});

test("MealLogs & FoodPresets: valid Kotlin payloads, list queries, toggle updates, and @DocumentId trap guard", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 8000);
  const mealsCol = aliceDb.collection("users").doc(ALICE_UID).collection("mealLogs");
  const presetsCol = aliceDb.collection("users").doc(ALICE_UID).collection("foodPresets");

  // Empty subcollection list queries must succeed cleanly
  await assertSucceeds(mealsCol.where("dateKey", "==", "2026-11-19").get());
  await assertSucceeds(presetsCol.get());

  // Valid create (no "id" key, completed not isCompleted)
  await assertSucceeds(
    mealsCol.doc("meal_abc").set({
      userId: ALICE_UID,
      dateKey: "2026-11-19",
      mealSlot: "DINNER",
      name: "Chicken Pesto Pasta",
      calories: 860,
      proteinGrams: 52,
      carbsGrams: 90,
      fatGrams: 28,
      completed: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject including "id" key in body (@DocumentId parity)
  await assertFails(
    mealsCol.doc("meal_bad_id").set({
      id: "meal_bad_id",
      userId: ALICE_UID,
      dateKey: "2026-11-19",
      mealSlot: "SNACK",
      name: "Protein Bar",
      calories: 240,
      proteinGrams: 20,
      carbsGrams: 24,
      fatGrams: 7,
      completed: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Partial completion toggle
  await assertSucceeds(
    mealsCol.doc("meal_abc").update({
      completed: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Preset create & favorite toggle
  await assertSucceeds(
    presetsCol.doc("preset_1").set({
      userId: ALICE_UID,
      name: "Mass Gainer Oats",
      category: "Breakfast",
      servingLabel: "1 bowl",
      calories: 720,
      proteinGrams: 40,
      carbsGrams: 86,
      fatGrams: 22,
      favorite: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    presetsCol.doc("preset_1").update({
      favorite: true,
      updatedAt: new Date(Date.now() - 400),
    })
  );
});

test("WeightEntries, DailyActivities, and WatchSnapshots enforce dateKey ID match and optional fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 6000);
  const dateKey = "2026-11-19";

  // WeightEntry create + update + invalid weight bound
  const weightRef = aliceDb.collection("users").doc(ALICE_UID).collection("weightEntries").doc(dateKey);
  await assertSucceeds(
    weightRef.set({
      userId: ALICE_UID,
      dateKey,
      weightKg: 66.8,
      note: "Morning check-in",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    weightRef.update({
      weightKg: 67.0,
      note: "Post-breakfast",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("weightEntries").doc("2026-11-20").set({
      userId: ALICE_UID,
      dateKey: "2026-11-19", // mismatched dateKey vs docId
      weightKg: 67.0,
      note: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // DailyActivity create + water update + workout update
  const actRef = aliceDb.collection("users").doc(ALICE_UID).collection("dailyActivities").doc(dateKey);
  await assertSucceeds(
    actRef.set({
      userId: ALICE_UID,
      dateKey,
      waterMl: 500,
      workoutCompleted: false,
      workoutLabel: "",
      caloriesBurned: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    actRef.update({
      waterMl: 750,
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertSucceeds(
    actRef.update({
      workoutCompleted: true,
      workoutLabel: "Hypertrophy Lift",
      caloriesBurned: 260,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // WatchSnapshot create (omitted null optional fields) & update
  const watchRef = aliceDb.collection("users").doc(ALICE_UID).collection("watchSnapshots").doc(dateKey);
  await assertSucceeds(
    watchRef.set({
      userId: ALICE_UID,
      dateKey,
      stepsToday: 8420,
      avgHeartRateBpm: 72,
      activeBurnedKcal: 340,
      watchName: "Pixel Watch 3",
      batteryPercent: 86,
      sourceLabel: "Health Connect",
      syncedAtMillis: Date.now() - 5000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    watchRef.update({
      stepsToday: 9100,
      syncedAtMillis: Date.now() - 1000,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});
