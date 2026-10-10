const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { after, before, beforeEach, describe, it } = require("node:test");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const {
  collection,
  deleteDoc,
  doc,
  deleteField,
  getDoc,
  getDocs,
  query,
  setDoc,
  updateDoc,
  where,
} = require("firebase/firestore");

describe("Firestore security rules", () => {
  let testEnvironment;

  before(async () => {
    const [host, port] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080")
      .split(":");

    testEnvironment = await initializeTestEnvironment({
      projectId: "demo-spotted-rules",
      firestore: {
        host,
        port: Number(port),
        rules: fs.readFileSync(path.join(__dirname, "firestore.rules"), "utf8"),
      },
    });
  });

  beforeEach(async () => {
    await testEnvironment.clearFirestore();
  });

  after(async () => {
    await testEnvironment.cleanup();
  });

  async function seedPet(id, ownerId) {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "pets", id), {
        ownerId,
        name: `Pet ${id}`,
      });
    });
  }

  async function seedUser(uid) {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "users", uid), userProfile(uid));
    });
  }

  function userProfile(uid, overrides = {}) {
    return {
      uid,
      name: "Jamie",
      phone: "+41 79 123 45 67",
      email: "jamie@example.com",
      homeArea: "Lausanne",
      ...overrides,
    };
  }

  function firestoreForUser(userId) {
    return userId === null
      ? testEnvironment.unauthenticatedContext().firestore()
      : testEnvironment.authenticatedContext(userId).firestore();
  }

  const createCases = [
    { who: "owner", userId: "owner-1", petOwnerId: "owner-1", allowed: true },
    {
      who: "user creating for another owner",
      userId: "owner-1",
      petOwnerId: "owner-2",
      allowed: false,
    },
    { who: "anonymous user", userId: null, petOwnerId: "owner-1", allowed: false },
  ];

  for (const testCase of createCases) {
    it(`create: ${testCase.who} ${testCase.allowed ? "is allowed" : "is denied"}`, async () => {
      const db = firestoreForUser(testCase.userId);
      const operation = setDoc(doc(db, "pets", "pet-1"), {
        ownerId: testCase.petOwnerId,
        name: "Milo",
      });

      if (testCase.allowed) {
        await assertSucceeds(operation);
      } else {
        await assertFails(operation);
      }
    });
  }

  const readCases = [
    { who: "owner", userId: "owner-1", allowed: true },
    { who: "another user", userId: "owner-2", allowed: false },
    { who: "anonymous user", userId: null, allowed: false },
  ];

  for (const testCase of readCases) {
    it(`read: ${testCase.who} ${testCase.allowed ? "is allowed" : "is denied"}`, async () => {
      await seedPet("pet-1", "owner-1");
      const db = firestoreForUser(testCase.userId);
      const operation = getDoc(doc(db, "pets", "pet-1"));

      if (testCase.allowed) {
        const snapshot = await assertSucceeds(operation);
        assert.deepEqual(snapshot.data(), { ownerId: "owner-1", name: "Pet pet-1" });
      } else {
        await assertFails(operation);
      }
    });
  }

  it("requires owner-constrained queries to read pets", async () => {
    await seedPet("pet-1", "owner-1");
    await seedPet("pet-2", "owner-2");
    const ownerDb = testEnvironment.authenticatedContext("owner-1").firestore();

    const ownedPetsQuery = query(collection(ownerDb, "pets"), where("ownerId", "==", "owner-1"));
    const unfilteredQuery = collection(ownerDb, "pets");
    const otherOwnersQuery = query(
      collection(ownerDb, "pets"),
      where("ownerId", "==", "owner-2"),
    );

    const results = await assertSucceeds(getDocs(ownedPetsQuery));
    assert.deepEqual(results.docs.map((snapshot) => snapshot.id), ["pet-1"]);
    await assertFails(getDocs(unfilteredQuery));
    await assertFails(getDocs(otherOwnersQuery));
  });

  const updateCases = [
    { who: "owner", userId: "owner-1", allowed: true },
    { who: "another user", userId: "owner-2", allowed: false },
    { who: "anonymous user", userId: null, allowed: false },
  ];

  for (const testCase of updateCases) {
    it(`update: ${testCase.who} ${testCase.allowed ? "is allowed" : "is denied"}`, async () => {
      await seedPet("pet-1", "owner-1");
      const db = firestoreForUser(testCase.userId);
      const operation = updateDoc(doc(db, "pets", "pet-1"), { name: "Updated name" });

      if (testCase.allowed) {
        await assertSucceeds(operation);
      } else {
        await assertFails(operation);
      }
    });
  }

  it("update: owner cannot transfer a pet to another owner", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser("owner-1");

    await assertFails(updateDoc(doc(db, "pets", "pet-1"), { ownerId: "owner-2" }));
  });

  const deleteCases = [
    { who: "owner", userId: "owner-1", allowed: true },
    { who: "another user", userId: "owner-2", allowed: false },
    { who: "anonymous user", userId: null, allowed: false },
  ];

  for (const testCase of deleteCases) {
    it(`delete: ${testCase.who} ${testCase.allowed ? "is allowed" : "is denied"}`, async () => {
      await seedPet("pet-1", "owner-1");
      const db = firestoreForUser(testCase.userId);
      const operation = deleteDoc(doc(db, "pets", "pet-1"));

      if (testCase.allowed) {
        await assertSucceeds(operation);
      } else {
        await assertFails(operation);
      }
    });
  }

  it("denies reads and writes to unmatched collections", async () => {
    const ownerDb = testEnvironment.authenticatedContext("owner-1").firestore();

    await assertFails(getDoc(doc(ownerDb, "some_unmatched_collection", "doc-1")));
    await assertFails(
      setDoc(doc(ownerDb, "some_unmatched_collection", "doc-1"), { ownerId: "owner-1" }),
    );
  });

  const userCreateCases = [
    { who: "owner", userId: "user-1", profileUid: "user-1", allowed: true },
    { who: "another user", userId: "user-2", profileUid: "user-1", allowed: false },
    { who: "anonymous user", userId: null, profileUid: "user-1", allowed: false },
  ];

  for (const testCase of userCreateCases) {
    it(`user create: ${testCase.who} ${testCase.allowed ? "is allowed" : "is denied"}`, async () => {
      const db = firestoreForUser(testCase.userId);
      const operation = setDoc(
        doc(db, "users", testCase.profileUid),
        userProfile(testCase.profileUid),
      );

      if (testCase.allowed) {
        await assertSucceeds(operation);
      } else {
        await assertFails(operation);
      }
    });
  }

  const userReadCases = [
    { who: "owner", userId: "user-1", allowed: true },
    { who: "another user", userId: "user-2", allowed: false },
    { who: "anonymous user", userId: null, allowed: false },
  ];

  for (const testCase of userReadCases) {
    it(`user read: ${testCase.who} ${testCase.allowed ? "is allowed" : "is denied"}`, async () => {
      await seedUser("user-1");
      const db = firestoreForUser(testCase.userId);
      const operation = getDoc(doc(db, "users", "user-1"));

      if (testCase.allowed) {
        const snapshot = await assertSucceeds(operation);
        assert.deepEqual(snapshot.data(), userProfile("user-1"));
      } else {
        await assertFails(operation);
      }
    });
  }

  it("denies listing user profiles, including to authenticated users", async () => {
    await seedUser("user-1");
    const db = firestoreForUser("user-1");

    await assertFails(getDocs(collection(db, "users")));
  });

  it("allows an owner to check whether their profile exists", async () => {
    const db = firestoreForUser("user-1");

    const snapshot = await assertSucceeds(getDoc(doc(db, "users", "user-1")));
    assert.equal(snapshot.exists(), false);
  });

  it("allows an owner to update their valid profile", async () => {
    await seedUser("user-1");
    const db = firestoreForUser("user-1");

    await assertSucceeds(
      updateDoc(doc(db, "users", "user-1"), { name: "Alex" }),
    );
  });

  const userUpdateCases = [
    { who: "another user", userId: "user-2", allowed: false },
    { who: "anonymous user", userId: null, allowed: false },
  ];

  for (const testCase of userUpdateCases) {
    it(`user update: ${testCase.who} is denied`, async () => {
      await seedUser("user-1");
      const db = firestoreForUser(testCase.userId);

      await assertFails(
        updateDoc(doc(db, "users", "user-1"), { name: "Alex" }),
      );
    });
  }

  it("denies profile creation with a mismatched uid", async () => {
    const db = firestoreForUser("user-1");

    await assertFails(
      setDoc(doc(db, "users", "user-1"), userProfile("user-2")),
    );
  });

  it("denies profile updates that change uid or add unknown fields", async () => {
    await seedUser("user-1");
    const db = firestoreForUser("user-1");
    const profile = doc(db, "users", "user-1");

    await assertFails(updateDoc(profile, { uid: "user-2" }));
    await assertFails(updateDoc(profile, { extraData: "not allowed" }));
  });

  it("denies invalid and oversized profile fields", async () => {
    const db = firestoreForUser("user-1");

    await assertFails(
      setDoc(doc(db, "users", "user-1"), userProfile("user-1", { name: 42 })),
    );
    await assertFails(
      setDoc(
        doc(db, "users", "user-1"),
        userProfile("user-1", { name: "x".repeat(101) }),
      ),
    );
  });

  it("denies profile updates that omit required or invalid fields", async () => {
    await seedUser("user-1");
    const db = firestoreForUser("user-1");
    const profile = doc(db, "users", "user-1");

    await assertFails(updateDoc(profile, { phone: deleteField() }));
    await assertFails(updateDoc(profile, { name: 42 }));
    await assertFails(updateDoc(profile, { name: "x".repeat(101) }));
  });

  it("denies profile deletion", async () => {
    await seedUser("user-1");
    const db = firestoreForUser("user-1");

    await assertFails(deleteDoc(doc(db, "users", "user-1")));
  });
});

describe("Firestore alert access rules", () => {
  let testEnvironment;

  before(async () => {
    const [host, port] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080").split(":");

    testEnvironment = await initializeTestEnvironment({
      projectId: "demo-spotted-rules",
      firestore: {
        host,
        port: Number(port),
        rules: fs.readFileSync(path.join(__dirname, "firestore.rules"), "utf8"),
      },
    });
  });

  beforeEach(async () => {
    await testEnvironment.clearFirestore();
  });

  after(async () => {
    await testEnvironment.cleanup();
  });

  async function seedPet(id, ownerId) {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "pets", id), {
        ownerId,
        name: `Pet ${id}`,
      });
    });
  }

  function firestoreForUser(userId) {
    return userId === null
      ? testEnvironment.unauthenticatedContext().firestore()
      : testEnvironment.authenticatedContext(userId).firestore();
  }

  it("create: owner of the pet is allowed to create an alert", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser("owner-1");

    await assertSucceeds(
      setDoc(doc(db, "alerts", "alert-1"), {
        id: "alert-1",
        ownerId: "owner-1",
        petId: "pet-1",
        lostAtMillis: 1000000,
        status: "OPEN",
        petName: "Rex",
        petSpecies: "DOG",
        petAllergies: [],
        petBehaviors: [],
        lastKnownLocation: {
            latitude: 46.5,
            longitude: 6.6
        }
      })
    );
  });

  it("create: user creating an alert for someone else's pet is denied", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser("owner-2");

    await assertFails(
      setDoc(doc(db, "alerts", "alert-2"), {
        id: "alert-2",
        ownerId: "owner-2",
        petId: "pet-1",
        lostAtMillis: 1000000,
        status: "OPEN",
        petName: "Rex",
        petSpecies: "DOG",
        petAllergies: [],
        petBehaviors: [],
        lastKnownLocation: {
            latitude: 46.5,
            longitude: 6.6
        }
      })
    );
  });

  it("create: anonymous user is denied", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser(null);

    await assertFails(
      setDoc(doc(db, "alerts", "alert-3"), {
        id: "alert-3",
        ownerId: "anonymous",
        petId: "pet-1",
        lostAtMillis: 1000000,
        status: "OPEN",
        petName: "Rex",
        petSpecies: "DOG",
        petAllergies: [],
        petBehaviors: [],
        lastKnownLocation: {
            latitude: 46.5,
            longitude: 6.6
        }
      })
    );
  });

  it("read: authenticated user can read alerts", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "alerts", "alert-1"), {
        id: "alert-1",
        ownerId: "owner-1",
        petId: "pet-1",
        lostAtMillis: 1000000,
        status: "OPEN",
        petName: "Rex",
        petSpecies: "DOG",
        petAllergies: [],
        petBehaviors: [],
        lastKnownLocation: {
            latitude: 46.5,
            longitude: 6.6
        }
      });
    });

    const authenticatedDb = firestoreForUser("user-1");
    await assertSucceeds(getDoc(doc(authenticatedDb, "alerts", "alert-1")));
  });

  it("read: anonymous user is denied to read alerts", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "alerts", "alert-1"), {
        id: "alert-1",
        ownerId: "owner-1",
        petId: "pet-1",
        lostAtMillis: 1000000,
        status: "OPEN",
        petName: "Rex",
        petSpecies: "DOG",
        petAllergies: [],
        petBehaviors: [],
        lastKnownLocation: {
            latitude: 46.5,
            longitude: 6.6
        }
      });
    });

    const anonymousDb = firestoreForUser(null);
    await assertFails(getDoc(doc(anonymousDb, "alerts", "alert-1")));
  });

  it("update: alert owner is allowed to update", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "alerts", "alert-1"), {
        id: "alert-1",
        ownerId: "owner-1",
        petId: "pet-1",
        lostAtMillis: 1000000,
        status: "OPEN",
        petName: "Rex",
        petSpecies: "DOG",
        petAllergies: [],
        petBehaviors: [],
        lastKnownLocation: {
            latitude: 46.5,
            longitude: 6.6
        }
      });
    });

    const db = firestoreForUser("owner-1");
    await assertSucceeds(
      updateDoc(doc(db, "alerts", "alert-1"), {
          status: "CLOSED",
          lastKnownLocation: {
              latitude: 47.0,
              longitude: 7.0
          }
      })
    );
  });

  it("delete: another user is denied to delete", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "alerts", "alert-1"), {
        id: "alert-1",
        ownerId: "owner-1",
        petId: "pet-1"
      });
    });

    const db = firestoreForUser("owner-2");
    await assertFails(deleteDoc(doc(db, "alerts", "alert-1")));
  });
});
