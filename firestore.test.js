const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_nexora_001";
const BOB_UID = "bob_nexora_002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msAgo = 5000) {
  return new Date(Date.now() - msAgo);
}

function validUserProfile(uid, ts = pastDate()) {
  return {
    userId: uid,
    displayName: "Alice Lin",
    email: "alice@example.com",
    preferredModel: "gemini-3.8-flash",
    themeMode: "DARK",
    language: "English",
    memoryEnabled: true,
    voiceEnabled: true,
    voiceAutoSpeak: false,
    voiceRate: 1.0,
    voicePitch: 1.0,
    notificationsEnabled: true,
    temperature: 0.7,
    maxOutputTokens: 4096,
    totalPromptTokens: 0,
    totalOutputTokens: 0,
    totalRequests: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validConversation(uid, ts = pastDate()) {
  return {
    userId: uid,
    title: "Kotlin Architecture Review",
    modelId: "gemini-3.8-flash",
    pinned: false,
    mode: "CHAT",
    lastMessagePreview: "Let us design clean MVVM modules.",
    messageCount: 1,
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

test("Unauthenticated users are denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validUserProfile(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(unauthDb.collection("users").get());
});

test("User profile: owner create, partial updates, usage increment, and strict isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserProfile(ALICE_UID, created))
  );

  // Partial settings update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themeMode: "LIGHT",
      bio: "AI Systems Engineer",
      updatedAt: pastDate(1000),
    })
  );

  // Usage counter update action
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalPromptTokens: 250,
      totalOutputTokens: 600,
      totalRequests: 1,
      updatedAt: pastDate(500),
    })
  );

  // Reject ghost field shadow update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(200),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );

  // Reject cross-user read and write
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: pastDate(100),
    })
  );

  // Reject user enumeration
  await assertFails(aliceDb.collection("users").get());
});

test("Conversations & Messages: Kotlin-aligned CRUD, parent existence, and isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(10000);

  // Orphaned message without parent conversation must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("missing_conv")
      .collection("messages")
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        conversationId: "missing_conv",
        role: "user",
        content: "Hello",
        promptTokens: 0,
        outputTokens: 0,
        edited: false,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Create conversation (no @DocumentId `id` field)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .set(validConversation(ALICE_UID, ts))
  );

  // Reject forbidden `id` key on create
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_bad")
      .set({ ...validConversation(ALICE_UID, ts), id: "conv_bad" })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_future")
      .set(validConversation(ALICE_UID, new Date(Date.now() + 60000)))
  );

  // Partial update: pin & rename
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .update({
        title: "Pinned Kotlin Review",
        pinned: true,
        updatedAt: pastDate(1000),
      })
  );

  // Create message under existing parent conversation
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        conversationId: "conv_1",
        role: "user",
        content: "Explain coroutines structured concurrency",
        promptTokens: 12,
        outputTokens: 0,
        edited: false,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Edit user message
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_1")
      .update({
        content: "Explain Kotlin coroutines structured concurrency in depth",
        edited: true,
        updatedAt: pastDate(500),
      })
  );

  // List own conversations & messages
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .get()
  );

  // Cross-user isolation
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .get()
  );
});

test("Folders, Assistants, Memories, Images, Notifications, and Feedback CRUD + Isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(8000);

  // Folder
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("folders").doc("fld_1").set({
      userId: ALICE_UID,
      name: "Engineering",
      colorHex: "#00D4FF",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Custom Assistant
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("assistants").doc("ast_1").set({
      userId: ALICE_UID,
      name: "Coding Assistant",
      tagline: "Senior Systems Engineer",
      personality: "Direct, architectural, test-driven",
      systemInstructions: "Provide clean idiomatic code with complexity notes.",
      modelId: "gemini-3.8-flash",
      iconName: "code",
      starterPrompt: "Review my Kotlin coroutine flow.",
      builtIn: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Memory Record
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      category: "PREFERENCE",
      content: "Prefers concise Kotlin code examples using Material 3.",
      active: true,
      consentConfirmed: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").update({
      active: false,
      updatedAt: pastDate(1000),
    })
  );

  // Generated Image Record
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("generated_images").doc("img_1").set({
      userId: ALICE_UID,
      prompt: "Minimalist glass prism in midnight studio",
      style: "Photorealistic",
      aspectRatio: "1:1",
      modelId: "gemini-nano-banana-2.1",
      localFilePath: "/files/generated_images/img_1.png",
      createdAt: ts,
    })
  );

  // Notification
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("ntf_1").set({
      userId: ALICE_UID,
      title: "Welcome to Nexora AI",
      body: "Your private workspace is synced and ready.",
      category: "SYSTEM",
      read: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("ntf_1").update({
      read: true,
      updatedAt: pastDate(500),
    })
  );

  // Feedback
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("feedback").doc("fb_1").set({
      userId: ALICE_UID,
      category: "Feature",
      subject: "Love the streaming markdown",
      message: "Fast, crisp typography and great code blocks.",
      rating: 5,
      createdAt: ts,
    })
  );

  // Bob cannot list or read Alice's private subcollections
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("assistants").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notifications").get());
});
