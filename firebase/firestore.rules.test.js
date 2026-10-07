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
  getDoc,
  getDocs,
  query,
  setDoc,
  updateDoc,
  where,
} = require("firebase/firestore");

describe("Firestore pet access rules", () => {
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

  it("read: anyone can read alerts", async () => {
    // Seed an alert bypassing rules
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
    await assertSucceeds(getDoc(doc(anonymousDb, "alerts", "alert-1")));
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
