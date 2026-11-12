// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
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
  limit,
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

  it("another user cannot update or delete a pet", async () => {
    await seedPet("pet-1", "owner-1");
    const otherUserDb = firestoreForUser("owner-2");

    await assertFails(updateDoc(doc(otherUserDb, "pets", "pet-1"), { name: "Changed" }));
    await assertFails(deleteDoc(doc(otherUserDb, "pets", "pet-1")));
    const ownerPet = await getDoc(doc(firestoreForUser("owner-1"), "pets", "pet-1"));
    assert.deepEqual(ownerPet.data(), { ownerId: "owner-1", name: "Pet pet-1" });
  });

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

  it("allows users to delete their own profile", async () => {
    await seedUser("user-1");
    const db = firestoreForUser("user-1");

    await assertSucceeds(deleteDoc(doc(db, "users", "user-1")));
  });

  it("denies deleting another user's profile", async () => {
    await seedUser("user-1");

    await assertFails(deleteDoc(doc(firestoreForUser("user-2"), "users", "user-1")));
    await assertFails(deleteDoc(doc(firestoreForUser(null), "users", "user-1")));
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
          closedAtMillis: 2000000,
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

  function alertData(overrides = {}) {
    return {
      id: "alert-1",
      ownerId: "owner-1",
      petId: "pet-1",
      lostAtMillis: 1000000,
      closedAtMillis: null,
      status: "OPEN",
      petName: "Rex",
      petSpecies: "DOG",
      petAllergies: [],
      petBehaviors: [],
      lastKnownLocation: { latitude: 46.5, longitude: 6.6 },
      ...overrides,
    };
  }

  async function seedAlert(overrides = {}) {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "alerts", "alert-1"), alertData(overrides));
    });
  }

  const closing = { status: "CLOSED", closedAtMillis: 2000000 };

  it("create: alert with a null closedAtMillis, as the app writes it, is allowed", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser("owner-1");

    await assertSucceeds(setDoc(doc(db, "alerts", "alert-1"), alertData()));
  });

  it("create: alert that is already closed is denied", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser("owner-1");

    await assertFails(setDoc(doc(db, "alerts", "alert-1"), alertData(closing)));
  });

  it("close: owner is allowed to close their open alert", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertSucceeds(updateDoc(doc(db, "alerts", "alert-1"), closing));
  });

  it("close: another user is denied", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-2");

    await assertFails(updateDoc(doc(db, "alerts", "alert-1"), closing));
  });

  it("close: anonymous user is denied", async () => {
    await seedAlert();
    const db = firestoreForUser(null);

    await assertFails(updateDoc(doc(db, "alerts", "alert-1"), closing));
  });

  it("close: owner can close a legacy alert stored without a status", async () => {
    const { status, ...legacyAlert } = alertData();
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "alerts", "alert-1"), legacyAlert);
    });
    const db = firestoreForUser("owner-1");

    await assertSucceeds(updateDoc(doc(db, "alerts", "alert-1"), closing));
  });

  it("update: open alert cannot carry a closing time", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertFails(updateDoc(doc(db, "alerts", "alert-1"), { closedAtMillis: 2000000 }));
  });

  it("update: owner cannot change the ownerId", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertFails(updateDoc(doc(db, "alerts", "alert-1"), { ownerId: "owner-2" }));
  });

  it("reopen: owner is denied to reopen a closed alert", async () => {
    await seedAlert(closing);
    const db = firestoreForUser("owner-1");

    await assertFails(
      updateDoc(doc(db, "alerts", "alert-1"), { status: "OPEN", closedAtMillis: null })
    );
  });

  it("reopen: another user is denied to reopen a closed alert", async () => {
    await seedAlert(closing);
    const db = firestoreForUser("owner-2");

    await assertFails(
      updateDoc(doc(db, "alerts", "alert-1"), { status: "OPEN", closedAtMillis: null })
    );
  });

  it("reopen: anonymous user is denied to reopen a closed alert", async () => {
    await seedAlert(closing);
    const db = firestoreForUser(null);

    await assertFails(
      updateDoc(doc(db, "alerts", "alert-1"), { status: "OPEN", closedAtMillis: null })
    );
  });

  it("update: owner cannot move a closed alert", async () => {
    await seedAlert(closing);
    const db = firestoreForUser("owner-1");

    await assertFails(
      updateDoc(doc(db, "alerts", "alert-1"), {
        lastKnownLocation: { latitude: 47.0, longitude: 7.0 },
      })
    );
  });

  it("close: closing while also changing the ownerId is denied", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertFails(
      updateDoc(doc(db, "alerts", "alert-1"), { ...closing, ownerId: "owner-2" })
    );
  });

  it("close: closing while also renaming the pet is denied", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertFails(updateDoc(doc(db, "alerts", "alert-1"), { ...closing, petName: "Max" }));
  });

  it("close: closed status without a closing time is denied", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertFails(updateDoc(doc(db, "alerts", "alert-1"), { status: "CLOSED" }));
  });

  it("close: closed status with a null closing time is denied", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertFails(
      updateDoc(doc(db, "alerts", "alert-1"), { status: "CLOSED", closedAtMillis: null })
    );
  });

  it("close: closing time in the future is denied", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertFails(
      updateDoc(doc(db, "alerts", "alert-1"), {
        status: "CLOSED",
        closedAtMillis: Date.now() + 10 * 60 * 1000,
      })
    );
  });

  it("close: closing time before the pet was lost is denied", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertFails(
      updateDoc(doc(db, "alerts", "alert-1"), { status: "CLOSED", closedAtMillis: 999999 })
    );
  });

  it("update: unknown status value is denied", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertFails(updateDoc(doc(db, "alerts", "alert-1"), { status: "foo" }));
  });

  it("update: owner can move their open alert", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertSucceeds(
      updateDoc(doc(db, "alerts", "alert-1"), {
        lastKnownLocation: { latitude: 47.0, longitude: 7.0 },
      })
    );
  });

  it("update: owner cannot rename the pet of their open alert", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertFails(updateDoc(doc(db, "alerts", "alert-1"), { petName: "Max" }));
  });

  const seenThere = (lastSeenAtMillis) => ({
    lastKnownLocation: { latitude: 46.52, longitude: 6.63 },
    lastSeenAtMillis,
  });

  it("last seen: owner is allowed to move their open alert to a newer sighting", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertSucceeds(updateDoc(doc(db, "alerts", "alert-1"), seenThere(1500000)));
  });

  it("last seen: another user is denied", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-2");

    await assertFails(updateDoc(doc(db, "alerts", "alert-1"), seenThere(1500000)));
  });

  it("last seen: anonymous user is denied", async () => {
    await seedAlert();
    const db = testEnvironment.unauthenticatedContext().firestore();

    await assertFails(updateDoc(doc(db, "alerts", "alert-1"), seenThere(1500000)));
  });

  it("last seen: a time older than the current last seen is denied", async () => {
    await seedAlert({ lastSeenAtMillis: 1500000 });
    const db = firestoreForUser("owner-1");

    await assertFails(updateDoc(doc(db, "alerts", "alert-1"), seenThere(1200000)));
  });

  it("last seen: a time before the pet was lost is denied", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertFails(updateDoc(doc(db, "alerts", "alert-1"), seenThere(999999)));
  });

  it("last seen: a time far in the future is denied", async () => {
    await seedAlert();
    const db = firestoreForUser("owner-1");

    await assertFails(
      updateDoc(doc(db, "alerts", "alert-1"), seenThere(Date.now() + 60 * 60 * 1000))
    );
  });

  it("last seen: removing the last seen time is denied", async () => {
    await seedAlert({ lastSeenAtMillis: 1500000 });
    const db = firestoreForUser("owner-1");

    await assertFails(updateDoc(doc(db, "alerts", "alert-1"), { lastSeenAtMillis: null }));
  });

  it("last seen: a closed alert can't be moved", async () => {
    await seedAlert(closing);
    const db = firestoreForUser("owner-1");

    await assertFails(updateDoc(doc(db, "alerts", "alert-1"), seenThere(2500000)));
  });
});

describe("Firestore sighting access rules", () => {
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

  function sighting(id, reporterId, overrides = {}) {
    return {
      id,
      reporterId,
      location: { latitude: 46.5, longitude: 6.6 },
      seenAtMillis: 1000000,
      species: "DOG",
      photoUrl: null,
      alertId: "alert-1",
      headingDegrees: null,
      ...overrides,
    };
  }

  async function seedSighting(id, reporterId, overrides = {}) {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "sightings", id), sighting(id, reporterId, overrides));
    });
  }

  function firestoreForUser(userId) {
    return userId === null
      ? testEnvironment.unauthenticatedContext().firestore()
      : testEnvironment.authenticatedContext(userId).firestore();
  }

  const createCases = [
    { who: "reporter", userId: "user-1", reporterId: "user-1", allowed: true },
    {
      who: "user reporting for another user",
      userId: "user-1",
      reporterId: "user-2",
      allowed: false,
    },
    { who: "anonymous user", userId: null, reporterId: "user-1", allowed: false },
  ];

  for (const testCase of createCases) {
    it(`sighting create: ${testCase.who} ${testCase.allowed ? "is allowed" : "is denied"}`, async () => {
      await seedAlert("alert-1", "owner-1");
      const db = firestoreForUser(testCase.userId);
      const operation = setDoc(
        doc(db, "sightings", "sighting-1"),
        sighting("sighting-1", testCase.reporterId),
      );

      if (testCase.allowed) {
        await assertSucceeds(operation);
      } else {
        await assertFails(operation);
      }
    });
  }

  it("sighting create: allows a document without headingDegrees", async () => {
    await seedAlert("alert-1", "owner-1");
    const db = firestoreForUser("user-1");
    const sightingWithoutHeading = sighting("sighting-1", "user-1");
    delete sightingWithoutHeading.headingDegrees;

    await assertSucceeds(
      setDoc(doc(db, "sightings", "sighting-1"), sightingWithoutHeading),
    );
  });

  const readCases = [
    { who: "reporter", userId: "user-1", allowed: true },
    { who: "another user", userId: "user-2", allowed: true },
    { who: "anonymous user", userId: null, allowed: false },
  ];

  for (const testCase of readCases) {
    it(`sighting read: ${testCase.who} ${testCase.allowed ? "is allowed" : "is denied"}`, async () => {
      await seedSighting("sighting-1", "user-1");
      const db = firestoreForUser(testCase.userId);
      const operation = getDoc(doc(db, "sightings", "sighting-1"));

      if (testCase.allowed) {
        const snapshot = await assertSucceeds(operation);
        assert.deepEqual(snapshot.data(), sighting("sighting-1", "user-1"));
      } else {
        await assertFails(operation);
      }
    });
  }

  it("sighting list: signed-in users can query an alert's sightings with a limit", async () => {
    await seedSighting("sighting-1", "user-1");
    await seedSighting("sighting-2", "user-1", { alertId: "alert-2" });
    const db = firestoreForUser("user-2");
    const alertSightings = query(
      collection(db, "sightings"),
      where("alertId", "==", "alert-1"),
      limit(100),
    );

    const results = await assertSucceeds(getDocs(alertSightings));
    assert.deepEqual(results.docs.map((snapshot) => snapshot.id), ["sighting-1"]);
  });

  it("sighting list: queries without a limit or with a too large one are denied", async () => {
    const db = firestoreForUser("user-1");
    const sightings = collection(db, "sightings");

    await assertFails(getDocs(query(sightings, where("alertId", "==", "alert-1"))));
    await assertFails(getDocs(query(sightings, where("alertId", "==", "alert-1"), limit(101))));
  });

  it("sighting list: anonymous users are denied", async () => {
    const db = firestoreForUser(null);

    await assertFails(
      getDocs(query(collection(db, "sightings"), where("alertId", "==", "alert-1"), limit(100))),
    );
  });

  const updateCases = [
    { who: "reporter", userId: "user-1", allowed: true },
    { who: "another user", userId: "user-2", allowed: false },
    { who: "anonymous user", userId: null, allowed: false },
  ];

  for (const testCase of updateCases) {
    it(`sighting update: ${testCase.who} ${testCase.allowed ? "is allowed" : "is denied"}`, async () => {
      await seedSighting("sighting-1", "user-1");
      const db = firestoreForUser(testCase.userId);
      const operation = updateDoc(doc(db, "sightings", "sighting-1"), { species: "CAT" });

      if (testCase.allowed) {
        await assertSucceeds(operation);
      } else {
        await assertFails(operation);
      }
    });
  }

  it("sighting update: reporting the same id again replaces the reporter's sighting", async () => {
    await seedSighting("sighting-1", "user-1");
    const db = firestoreForUser("user-1");

    await assertSucceeds(
      setDoc(doc(db, "sightings", "sighting-1"), sighting("sighting-1", "user-1", { species: "CAT" })),
    );
  });

  it("sighting update: reporter cannot give the sighting to another user", async () => {
    await seedSighting("sighting-1", "user-1");
    const db = firestoreForUser("user-1");

    await assertFails(updateDoc(doc(db, "sightings", "sighting-1"), { reporterId: "user-2" }));
  });

  const deleteCases = [
    { who: "reporter", userId: "user-1", allowed: true },
    { who: "another user", userId: "user-2", allowed: false },
    { who: "anonymous user", userId: null, allowed: false },
  ];

  for (const testCase of deleteCases) {
    it(`sighting delete: ${testCase.who} ${testCase.allowed ? "is allowed" : "is denied"}`, async () => {
      await seedSighting("sighting-1", "user-1");
      const db = firestoreForUser(testCase.userId);
      const operation = deleteDoc(doc(db, "sightings", "sighting-1"));

      if (testCase.allowed) {
        await assertSucceeds(operation);
      } else {
        await assertFails(operation);
      }
    });
  }

  it("sighting create: optional photo and alert can be left out", async () => {
    const db = firestoreForUser("user-1");
    const { photoUrl, alertId, ...required } = sighting("sighting-1", "user-1");

    await assertSucceeds(setDoc(doc(db, "sightings", "sighting-1"), required));
  });

  it("sighting create: invalid documents are denied", async () => {
    const db = firestoreForUser("user-1");
    const ref = doc(db, "sightings", "sighting-1");
    const invalidSightings = [
      sighting("other-id", "user-1"),
      sighting("sighting-1", "user-1", { extraData: "not allowed" }),
      sighting("sighting-1", "user-1", { location: { latitude: 91, longitude: 6.6 } }),
      sighting("sighting-1", "user-1", { location: "Lausanne" }),
      sighting("sighting-1", "user-1", { seenAtMillis: "yesterday" }),
      sighting("sighting-1", "user-1", { species: "" }),
      sighting("sighting-1", "user-1", { species: "PARROT" }),
      sighting("sighting-1", "user-1", { photoUrl: 42 }),
      sighting("sighting-1", "user-1", { alertId: "" }),
      sighting("sighting-1", "user-1", { headingDegrees: -1 }),
      sighting("sighting-1", "user-1", { headingDegrees: 360 }),
      sighting("sighting-1", "user-1", { headingDegrees: 1.5 }),
      sighting("sighting-1", "user-1", { headingDegrees: "45" }),
    ];

    for (const invalidSighting of invalidSightings) {
      await assertFails(setDoc(ref, invalidSighting));
    }
  });

  it("sighting update: reporter cannot make the sighting invalid", async () => {
    await seedSighting("sighting-1", "user-1");
    const ref = doc(firestoreForUser("user-1"), "sightings", "sighting-1");
    const invalidChanges = [
      { id: "other-id" },
      { extraData: "not allowed" },
      { location: { latitude: 46.5, longitude: 181 } },
      { seenAtMillis: "yesterday" },
      { species: "PARROT" },
      { photoUrl: "x".repeat(2049) },
      { alertId: "" },
      { headingDegrees: 360 },
      { species: deleteField() },
    ];

    for (const invalidChange of invalidChanges) {
      await assertFails(updateDoc(ref, invalidChange));
    }
  });

  it("sighting update: reporter can preserve a legacy decimal heading", async () => {
    await seedSighting("sighting-1", "user-1", { headingDegrees: 45.5 });
    const ref = doc(firestoreForUser("user-1"), "sightings", "sighting-1");

    await assertSucceeds(updateDoc(ref, { species: "CAT" }));
    await assertFails(updateDoc(ref, { headingDegrees: 46.5 }));
  });

  it("sighting update: alert owner can review a sighting with a legacy decimal heading", async () => {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("sighting-1", "user-1", { headingDegrees: 45.5 });
    const ref = doc(firestoreForUser("owner-1"), "sightings", "sighting-1");

    await assertSucceeds(updateDoc(ref, { status: "CONFIRMED" }));
  });

  // Only the fields the sighting rules read; the alert schema is tested above.
  async function seedAlert(id, ownerId) {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "alerts", id), { id, ownerId, status: "OPEN" });
    });
  }

  it("sighting create: accepts only nullable headings in the range [0, 360)", async () => {
    await seedAlert("alert-1", "owner-1");
    const db = firestoreForUser("user-1");

    for (const [index, headingDegrees] of [null, 0, 45, 359].entries()) {
      await assertSucceeds(
        setDoc(
          doc(db, "sightings", `valid-heading-${index}`),
          sighting(`valid-heading-${index}`, "user-1", { headingDegrees }),
        ),
      );
    }

    for (const [index, headingDegrees] of [-1, 360, 45.5, "NE"].entries()) {
      await assertFails(
        setDoc(
          doc(db, "sightings", `invalid-heading-${index}`),
          sighting(`invalid-heading-${index}`, "user-1", { headingDegrees }),
        ),
      );
    }
  });

  const statusCases = [
    { who: "alert owner", userId: "owner-1", allowed: true },
    { who: "reporter", userId: "user-1", allowed: false },
    { who: "another user", userId: "user-2", allowed: false },
    { who: "anonymous user", userId: null, allowed: false },
  ];

  for (const testCase of statusCases) {
    it(`sighting status: ${testCase.who} ${testCase.allowed ? "is allowed" : "is denied"}`, async () => {
      await seedAlert("alert-1", "owner-1");
      await seedSighting("sighting-1", "user-1");
      const db = firestoreForUser(testCase.userId);
      const operation = updateDoc(doc(db, "sightings", "sighting-1"), { status: "CONFIRMED" });

      if (testCase.allowed) {
        await assertSucceeds(operation);
      } else {
        await assertFails(operation);
      }
    });
  }

  it("sighting status: alert owner can change their mind, but only to a known status", async () => {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("sighting-1", "user-1", { status: "CONFIRMED" });
    const ref = doc(firestoreForUser("owner-1"), "sightings", "sighting-1");

    await assertSucceeds(updateDoc(ref, { status: "DISMISSED" }));
    await assertSucceeds(updateDoc(ref, { status: "PENDING" }));
    await assertFails(updateDoc(ref, { status: "MAYBE" }));
    await assertFails(updateDoc(ref, { status: deleteField() }));
  });

  it("sighting status: alert owner cannot change anything else", async () => {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("sighting-1", "user-1");
    const ref = doc(firestoreForUser("owner-1"), "sightings", "sighting-1");

    await assertFails(updateDoc(ref, { status: "CONFIRMED", species: "CAT" }));
    await assertFails(updateDoc(ref, { alertId: "alert-2" }));
  });

  it("sighting status: denied when the sighting has no alert or the alert is missing", async () => {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("no-alert", "user-1", { alertId: null });
    await seedSighting("missing-alert", "user-1", { alertId: "alert-404" });
    const db = firestoreForUser("owner-1");

    await assertFails(updateDoc(doc(db, "sightings", "no-alert"), { status: "CONFIRMED" }));
    await assertFails(updateDoc(doc(db, "sightings", "missing-alert"), { status: "CONFIRMED" }));
  });

  it("sighting status: a new sighting must be pending", async () => {
    await seedAlert("alert-1", "owner-1");
    const ref = doc(firestoreForUser("user-1"), "sightings", "sighting-1");

    await assertFails(setDoc(ref, sighting("sighting-1", "user-1", { status: "CONFIRMED" })));
    await assertSucceeds(setDoc(ref, sighting("sighting-1", "user-1", { status: "PENDING" })));
  });

  for (const status of ["CONFIRMED", "DISMISSED"]) {
    it(`sighting status: reporter cannot change or delete a ${status} sighting`, async () => {
      await seedSighting("sighting-1", "user-1", { status });
      const ref = doc(firestoreForUser("user-1"), "sightings", "sighting-1");

      // Moving it would change the likely area, or put a reviewed sighting on another alert
      await assertFails(updateDoc(ref, { location: { latitude: 46.6, longitude: 6.7 } }));
      await assertFails(updateDoc(ref, { alertId: "alert-2" }));
      await assertFails(updateDoc(ref, { species: "CAT" }));
      await assertFails(setDoc(ref, sighting("sighting-1", "user-1", { status })));
      await assertFails(deleteDoc(ref));
    });
  }

  it("sighting status: reporter can edit and delete again after the owner undoes a review", async () => {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("sighting-1", "user-1", { status: "CONFIRMED" });
    await assertSucceeds(
      updateDoc(doc(firestoreForUser("owner-1"), "sightings", "sighting-1"), { status: "PENDING" }),
    );
    const ref = doc(firestoreForUser("user-1"), "sightings", "sighting-1");

    await assertSucceeds(updateDoc(ref, { species: "CAT" }));
    await assertSucceeds(deleteDoc(ref));
  });

  it("sighting update: reporter cannot move a pending sighting to another alert", async () => {
    await seedSighting("sighting-1", "user-1");
    const ref = doc(firestoreForUser("user-1"), "sightings", "sighting-1");

    await assertFails(updateDoc(ref, { alertId: "alert-2" }));
    await assertFails(updateDoc(ref, { alertId: null }));
    await assertFails(updateDoc(ref, { alertId: deleteField() }));
  });

  it("sighting update: reporter can link a sighting that had no alert", async () => {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("sighting-1", "user-1", { alertId: null });
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      const { alertId, ...withoutAlert } = sighting("sighting-2", "user-1");
      await setDoc(doc(context.firestore(), "sightings", "sighting-2"), withoutAlert);
    });
    const db = firestoreForUser("user-1");

    await assertSucceeds(updateDoc(doc(db, "sightings", "sighting-1"), { alertId: "alert-1" }));
    await assertSucceeds(updateDoc(doc(db, "sightings", "sighting-2"), { alertId: "alert-1" }));
  });

  it("sighting update: linking to the reporter's own alert is denied", async () => {
    await seedAlert("alert-1", "user-1");
    await seedSighting("sighting-1", "user-1", { alertId: null });
    const ref = doc(firestoreForUser("user-1"), "sightings", "sighting-1");

    await assertFails(updateDoc(ref, { alertId: "alert-1" }));
  });

  it("sighting update: moving the time more than 5 minutes ahead is denied", async () => {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("sighting-1", "user-1");
    const ref = doc(firestoreForUser("user-1"), "sightings", "sighting-1");

    await assertFails(updateDoc(ref, { seenAtMillis: Date.now() + fiveMinutesMillis + 60 * 1000 }));
  });

  const boundaryCases = [
    { field: "species", value: "DOG", allowed: true },
    { field: "species", value: "CAT", allowed: true },
    { field: "species", value: "OTHER", allowed: true },
    { field: "species", value: "dog", allowed: false },
    { field: "photoUrl", value: "x".repeat(2048), allowed: true },
    { field: "photoUrl", value: "x".repeat(2049), allowed: false },
    { field: "alertId", value: "a", allowed: true },
    { field: "alertId", value: "a".repeat(128), allowed: true },
    { field: "alertId", value: "a".repeat(129), allowed: false },
    { field: "location", value: { latitude: -90, longitude: -180 }, allowed: true },
    { field: "location", value: { latitude: 90, longitude: 180 }, allowed: true },
    { field: "location", value: { latitude: -90.1, longitude: 0 }, allowed: false },
    { field: "location", value: { latitude: 0, longitude: 180.1 }, allowed: false },
    { field: "headingDegrees", value: null, allowed: true },
    { field: "headingDegrees", value: 0, allowed: true },
    { field: "headingDegrees", value: 359, allowed: true },
    { field: "headingDegrees", value: -1, allowed: false },
    { field: "headingDegrees", value: 360, allowed: false },
    { field: "headingDegrees", value: 1.5, allowed: false },
    { field: "headingDegrees", value: "45", allowed: false },
  ];

  for (const testCase of boundaryCases) {
    const shown = JSON.stringify(testCase.value);
    const label = shown.length > 40 ? `${shown.length - 2} characters` : shown;
    it(`sighting create: ${testCase.field} ${label} ${testCase.allowed ? "is allowed" : "is denied"}`, async () => {
      // A new sighting needs its alert to exist; for the alertId cases, that alert is the one tested
      await seedAlert(testCase.field === "alertId" ? testCase.value : "alert-1", "owner-1");
      const db = firestoreForUser("user-1");
      const operation = setDoc(
        doc(db, "sightings", "sighting-1"),
        sighting("sighting-1", "user-1", { [testCase.field]: testCase.value }),
      );

      if (testCase.allowed) {
        await assertSucceeds(operation);
      } else {
        await assertFails(operation);
      }
    });
  }

  // Plausibility of a new sighting: not in the future, and only on an open alert of another user.

  const fiveMinutesMillis = 5 * 60 * 1000;

  it("sighting create: seen up to 5 minutes ahead is allowed", async () => {
    await seedAlert("alert-1", "owner-1");
    const db = firestoreForUser("user-1");
    // A second of margin for the time between Date.now() and the emulator's request.time
    const seenAtMillis = Date.now() + fiveMinutesMillis - 1000;

    await assertSucceeds(
      setDoc(doc(db, "sightings", "sighting-1"), sighting("sighting-1", "user-1", { seenAtMillis })),
    );
  });

  it("sighting create: seen more than 5 minutes ahead is denied", async () => {
    await seedAlert("alert-1", "owner-1");
    const db = firestoreForUser("user-1");
    const seenAtMillis = Date.now() + fiveMinutesMillis + 60 * 1000;

    await assertFails(
      setDoc(doc(db, "sightings", "sighting-1"), sighting("sighting-1", "user-1", { seenAtMillis })),
    );
  });

  it("sighting create: on an open alert of another user is allowed", async () => {
    await seedAlert("alert-1", "owner-1");
    const db = firestoreForUser("user-1");

    await assertSucceeds(setDoc(doc(db, "sightings", "sighting-1"), sighting("sighting-1", "user-1")));
  });

  it("sighting create: on the reporter's own alert is denied", async () => {
    await seedAlert("alert-1", "user-1");
    const db = firestoreForUser("user-1");

    await assertFails(setDoc(doc(db, "sightings", "sighting-1"), sighting("sighting-1", "user-1")));
  });

  it("sighting create: on a missing alert is denied", async () => {
    const db = firestoreForUser("user-1");

    await assertFails(setDoc(doc(db, "sightings", "sighting-1"), sighting("sighting-1", "user-1")));
  });

  it("sighting create: on a closed alert is denied", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "alerts", "alert-1"), {
        id: "alert-1",
        ownerId: "owner-1",
        status: "CLOSED",
      });
    });
    const db = firestoreForUser("user-1");

    await assertFails(setDoc(doc(db, "sightings", "sighting-1"), sighting("sighting-1", "user-1")));
  });

  it("sighting create: without an alert skips the alert checks", async () => {
    const db = firestoreForUser("user-1");

    await assertSucceeds(
      setDoc(doc(db, "sightings", "sighting-1"), sighting("sighting-1", "user-1", { alertId: null })),
    );
  });

  describe("pet tag access rules", () => {
    const tag = (tagId, petId, ownerId, extra = {}) => ({
      tagId,
      petId,
      ownerId,
      linkedAtMillis: 1000,
      showPhone: true,
      showEmail: true,
      contactPhone: "+41 79 123 45 67",
      contactEmail: "jamie@example.com",
      ...extra,
    });
    const seedPet = (id, ownerId) =>
      testEnvironment.withSecurityRulesDisabled((context) =>
        setDoc(doc(context.firestore(), "pets", id), { ownerId }),
      );
    const seedTag = (id, petId, ownerId) =>
      testEnvironment.withSecurityRulesDisabled((context) =>
        setDoc(doc(context.firestore(), "petTags", id), tag(id, petId, ownerId)),
      );

    it("allows finder reads and denies anonymous reads", async () => {
      await seedTag("A1B2C3D4", "pet-1", "owner-1");

      const ownerRead = getDoc(doc(firestoreForUser("owner-1"), "petTags", "A1B2C3D4"));
      const finderRead = getDoc(doc(firestoreForUser("owner-2"), "petTags", "A1B2C3D4"));
      await assertSucceeds(ownerRead);
      const foundTag = await assertSucceeds(finderRead);
      assert.equal(foundTag.data().contactPhone, "+41 79 123 45 67");
      assert.equal(foundTag.data().contactEmail, "jamie@example.com");
      await assertFails(getDoc(doc(firestoreForUser(null), "petTags", "A1B2C3D4")));
    });

    it("allows only owner-constrained tag queries", async () => {
      await seedTag("A1B2C3D4", "pet-1", "owner-1");
      const ownerTags = query(
        collection(firestoreForUser("owner-1"), "petTags"),
        where("ownerId", "==", "owner-1"),
      );

      await assertSucceeds(getDocs(ownerTags));
      await assertFails(getDocs(collection(firestoreForUser("owner-1"), "petTags")));
      await assertFails(
        getDocs(
          query(
            collection(firestoreForUser("owner-1"), "petTags"),
            where("ownerId", "==", "owner-2"),
          ),
        ),
      );
    });

    it("allows only pet owners to create tags with the exact schema", async () => {
      await seedPet("pet-1", "owner-1");
      for (const [uid, ownerId, allowed] of [
        ["owner-1", "owner-1", true],
        ["owner-2", "owner-2", false],
        [null, "owner-1", false],
      ]) {
        const id = uid === "owner-1" ? "A1B2C3D4" : uid === "owner-2" ? "B1B2C3D4" : "C1B2C3D4";
        const operation = setDoc(
          doc(firestoreForUser(uid), "petTags", id),
          tag(id, "pet-1", ownerId),
        );
        await (allowed ? assertSucceeds(operation) : assertFails(operation));
      }
      await assertFails(
        setDoc(
          doc(firestoreForUser("owner-1"), "petTags", "D1B2C3D4"),
          tag("D1B2C3D4", "pet-1", "owner-1", { extraField: true }),
        ),
      );
      await assertFails(
        setDoc(
          doc(firestoreForUser("owner-1"), "petTags", "E1B2C3D4"),
          tag("E1B2C3D4", "pet-1", "owner-1", { address: "private address" }),
        ),
      );
    });

    it("lets owners move tags only between their own pets and keeps schema strict", async () => {
      await seedPet("pet-1", "owner-1");
      await seedPet("pet-2", "owner-1");
      await seedPet("pet-3", "owner-2");
      await seedTag("A1B2C3D4", "pet-1", "owner-1");
      const ref = doc(firestoreForUser("owner-1"), "petTags", "A1B2C3D4");

      await assertSucceeds(setDoc(ref, tag("A1B2C3D4", "pet-2", "owner-1")));
      await assertFails(setDoc(ref, tag("A1B2C3D4", "pet-3", "owner-1")));
      await assertFails(setDoc(ref, tag("A1B2C3D4", "pet-2", "owner-1", { extraField: true }))); //extraField is a member of the extras ellipse
      await assertFails(
        setDoc(
          doc(firestoreForUser("owner-2"), "petTags", "A1B2C3D4"),
          tag("A1B2C3D4", "pet-3", "owner-2"),
        ),
      );
    });

    it("denies a hidden contact with a supplied value", async () => {
      await seedPet("pet-1", "owner-1");
      const ref = doc(firestoreForUser("owner-1"), "petTags", "A1B2C3D4");
      await assertFails(
        setDoc(
          ref,
          tag("A1B2C3D4", "pet-1", "owner-1", {
            showEmail: false,
            contactEmail: "hidden@example.com",
          }),
        ),
      );
    });

    it("denies hiding both contacts", async () => {
      await seedPet("pet-1", "owner-1");
      await assertFails(
        setDoc(
          doc(firestoreForUser("owner-1"), "petTags", "A1B2C3D4"),
          tag("A1B2C3D4", "pet-1", "owner-1", {
            showPhone: false,
            showEmail: false,
            contactPhone: null,
            contactEmail: null,
          }),
        ),
      );
    });

    it("allows hiding either contact while keeping the other visible", async () => {
      await seedPet("pet-1", "owner-1");
      const ref = doc(firestoreForUser("owner-1"), "petTags", "A1B2C3D4");
      await assertSucceeds(setDoc(ref, tag("A1B2C3D4", "pet-1", "owner-1")));

      await assertSucceeds(updateDoc(ref, { showPhone: false, contactPhone: null }));
      let updatedTag = (await getDoc(ref)).data();
      assert.equal(updatedTag.showPhone, false);
      assert.equal(updatedTag.contactPhone, null);
      assert.equal(updatedTag.showEmail, true);

      await assertSucceeds(updateDoc(ref, { showPhone: true, contactPhone: "+41 79 123 45 67" }));
      await assertSucceeds(updateDoc(ref, { showEmail: false, contactEmail: null }));
      updatedTag = (await getDoc(ref)).data();
      assert.equal(updatedTag.showPhone, true);
      assert.equal(updatedTag.showEmail, false);
      assert.equal(updatedTag.contactEmail, null);
    });

    it("denies another user from changing tag contact visibility", async () => {
      await seedTag("A1B2C3D4", "pet-1", "owner-1");

      await assertFails(
        updateDoc(doc(firestoreForUser("owner-2"), "petTags", "A1B2C3D4"), {
          showPhone: false,
          contactPhone: null,
        }),
      );
    });

    it("allows only the pet owner to delete a tag", async () => {
      await seedPet("pet-1", "owner-1");
      for (const id of ["A1B2C3D4", "B1B2C3D4", "C1B2C3D4", "D1B2C3D4"]) {
        await seedTag(id, "pet-1", "owner-1");
      }

      await assertFails(deleteDoc(doc(firestoreForUser("owner-2"), "petTags", "A1B2C3D4")));
      await assertFails(deleteDoc(doc(firestoreForUser(null), "petTags", "B1B2C3D4")));
      await assertSucceeds(deleteDoc(doc(firestoreForUser("owner-1"), "petTags", "C1B2C3D4")));
      await assertSucceeds(deleteDoc(doc(firestoreForUser("owner-1"), "pets", "pet-1")));
      await assertSucceeds(deleteDoc(doc(firestoreForUser("owner-1"), "petTags", "D1B2C3D4")));
    });
  });
});

describe("Firestore photo access rules", () => {
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

  function firestoreForUser(userId) {
    return userId === null
      ? testEnvironment.unauthenticatedContext().firestore()
      : testEnvironment.authenticatedContext(userId).firestore();
  }

  function photo(ownerId, overrides = {}) {
    return { ownerId, contentType: "image/jpeg", data: "aGVsbG8=", ...overrides };
  }

  async function seedPhoto(id, ownerId) {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "photos", id), photo(ownerId));
    });
  }

  it("photo read: signed-in users are allowed, anonymous users are denied", async () => {
    await seedPhoto("pet-1", "user-1");

    await assertSucceeds(getDoc(doc(firestoreForUser("user-1"), "photos", "pet-1")));
    await assertSucceeds(getDoc(doc(firestoreForUser("user-2"), "photos", "pet-1")));
    await assertFails(getDoc(doc(firestoreForUser(null), "photos", "pet-1")));
  });

  it("photo list: is denied, photos are read one by one", async () => {
    await seedPhoto("pet-1", "user-1");

    await assertFails(getDocs(collection(firestoreForUser("user-1"), "photos")));
  });

  const createCases = [
    { who: "owner", userId: "user-1", data: photo("user-1"), allowed: true },
    { who: "user saving for another owner", userId: "user-1", data: photo("user-2"), allowed: false },
    { who: "anonymous user", userId: null, data: photo("user-1"), allowed: false },
    {
      who: "owner with another content type",
      userId: "user-1",
      data: photo("user-1", { contentType: "image/png" }),
      allowed: false,
    },
    {
      who: "owner with an extra field",
      userId: "user-1",
      data: photo("user-1", { name: "Milo" }),
      allowed: false,
    },
    {
      who: "owner with non-string data",
      userId: "user-1",
      data: photo("user-1", { data: 42 }),
      allowed: false,
    },
  ];

  for (const testCase of createCases) {
    it(`photo create: ${testCase.who} ${testCase.allowed ? "is allowed" : "is denied"}`, async () => {
      const operation = setDoc(doc(firestoreForUser(testCase.userId), "photos", "pet-1"), testCase.data);

      if (testCase.allowed) {
        await assertSucceeds(operation);
      } else {
        await assertFails(operation);
      }
    });
  }

  it("photo update: the owner can replace their photo, another user cannot", async () => {
    await seedPhoto("pet-1", "user-1");

    await assertSucceeds(
      setDoc(doc(firestoreForUser("user-1"), "photos", "pet-1"), photo("user-1", { data: "bmV3" })),
    );
    await assertFails(
      setDoc(doc(firestoreForUser("user-2"), "photos", "pet-1"), photo("user-2")),
    );
  });

  it("photo delete: the owner is allowed, other users are denied", async () => {
    // A pet photo and a profile photo follow the same rule
    for (const id of ["pet-1", "profile_user-1"]) {
      await seedPhoto(id, "user-1");

      await assertFails(deleteDoc(doc(firestoreForUser("user-2"), "photos", id)));
      await assertFails(deleteDoc(doc(firestoreForUser(null), "photos", id)));
      await assertSucceeds(deleteDoc(doc(firestoreForUser("user-1"), "photos", id)));
    }
  });
});
