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
  getCountFromServer,
  getDoc,
  getDocs,
  limit,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
  writeBatch,
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
      username: "jamie",
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

  // Optional pet details (#388): only the allowed values, birth year not in the future
  const PET_DETAILS = {
    sex: ["MALE", "FEMALE", "UNKNOWN"],
    neutered: ["YES", "NO", "UNKNOWN"],
    size: ["SMALL", "MEDIUM", "LARGE", "UNKNOWN"],
    lifestyle: ["INDOOR_ONLY", "INDOOR_AND_OUTDOOR", "OUTDOOR_ONLY", "UNKNOWN"],
    microchipped: ["YES", "NO", "UNKNOWN"],
  };

  function petWith(details) {
    return { ownerId: "owner-1", name: "Milo", ...details };
  }

  it("details: every allowed value is accepted on create and update", async () => {
    const db = firestoreForUser("owner-1");
    for (const [field, values] of Object.entries(PET_DETAILS)) {
      for (const value of values) {
        await assertSucceeds(setDoc(doc(db, "pets", `pet-${field}-${value}`), petWith({ [field]: value })));
        await assertSucceeds(updateDoc(doc(db, "pets", `pet-${field}-${value}`), { [field]: value }));
      }
    }
  });

  it("details: an unknown value is denied on create and update", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser("owner-1");
    for (const field of Object.keys(PET_DETAILS)) {
      for (const value of ["HUGE", "unknown", "", 42, null]) {
        await assertFails(setDoc(doc(db, "pets", "pet-new"), petWith({ [field]: value })));
        await assertFails(updateDoc(doc(db, "pets", "pet-1"), { [field]: value }));
      }
    }
  });

  it("details: a pet with all of them is accepted", async () => {
    const db = firestoreForUser("owner-1");

    await assertSucceeds(
      setDoc(
        doc(db, "pets", "pet-1"),
        petWith({
          birthYear: 2020,
          breedId: "labrador",
          sex: "FEMALE",
          neutered: "YES",
          size: "LARGE",
          lifestyle: "INDOOR_AND_OUTDOOR",
          microchipped: "NO",
        }),
      ),
    );
  });

  it("details: birth year and breed id may be null or absent", async () => {
    const db = firestoreForUser("owner-1");

    await assertSucceeds(setDoc(doc(db, "pets", "pet-1"), petWith({})));
    await assertSucceeds(setDoc(doc(db, "pets", "pet-2"), petWith({ birthYear: null, breedId: null })));
    await assertSucceeds(updateDoc(doc(db, "pets", "pet-2"), { birthYear: 2015, breedId: "siamese" }));
    await assertSucceeds(
      updateDoc(doc(db, "pets", "pet-2"), { birthYear: deleteField(), breedId: deleteField() }),
    );
  });

  it("details: the current year is accepted, a future or invalid birth year is denied", async () => {
    const db = firestoreForUser("owner-1");
    const thisYear = new Date().getUTCFullYear();

    await assertSucceeds(setDoc(doc(db, "pets", "pet-1"), petWith({ birthYear: thisYear })));
    await assertSucceeds(setDoc(doc(db, "pets", "pet-2"), petWith({ birthYear: 1950 })));
    for (const birthYear of [thisYear + 1, 1949, 2020.5, "2020"]) {
      await assertFails(setDoc(doc(db, "pets", "pet-3"), petWith({ birthYear })));
      await assertFails(updateDoc(doc(db, "pets", "pet-1"), { birthYear }));
    }
  });

  it("details: an empty, too long or non-string breed id is denied", async () => {
    const db = firestoreForUser("owner-1");

    for (const breedId of ["", "x".repeat(101), 42]) {
      await assertFails(setDoc(doc(db, "pets", "pet-1"), petWith({ breedId })));
    }
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
    for (const username of ["", "x".repeat(31), 42]) {
      await assertFails(
        setDoc(doc(db, "users", "user-1"), userProfile("user-1", { username })),
      );
    }
  });

  it("allows a profile without a username (apps older than the username)", async () => {
    const db = firestoreForUser("user-1");
    const { username, ...withoutUsername } = userProfile("user-1");

    await assertSucceeds(setDoc(doc(db, "users", "user-1"), withoutUsername));
    await assertSucceeds(
      updateDoc(doc(db, "users", "user-1"), { username: deleteField() }),
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

  /** A valid open alert of owner-1 for [petId]. */
  function openAlert(id, petId = "pet-1") {
    return {
      id,
      ownerId: "owner-1",
      petId,
      lostAtMillis: 1000000,
      status: "OPEN",
      petName: "Rex",
      petSpecies: "DOG",
      petAllergies: [],
      petBehaviors: [],
      lastKnownLocation: { latitude: 46.5, longitude: 6.6 },
    };
  }

  /** Writes the alert and the pet's lock naming it together, as publishAlert does. */
  function publishWithLock(db, alertId, petId = "pet-1") {
    const batch = writeBatch(db);
    batch.set(doc(db, "alerts", alertId), openAlert(alertId, petId));
    batch.set(doc(db, "openPetAlerts", petId), { alertId, ownerId: "owner-1" });
    return batch.commit();
  }

  it("create: owner of the pet is allowed to create an alert", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser("owner-1");

    await assertSucceeds(publishWithLock(db, "alert-1"));
  });

  it("create: an open alert written without the pet's lock is denied", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser("owner-1");

    await assertFails(setDoc(doc(db, "alerts", "alert-1"), openAlert("alert-1")));
  });

  it("create: a second open alert for the same pet is denied", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser("owner-1");
    await assertSucceeds(publishWithLock(db, "alert-1"));

    await assertFails(publishWithLock(db, "alert-2"));
  });

  it("lock: deleting the lock of an open alert is denied, so no second alert gets in", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser("owner-1");
    await assertSucceeds(publishWithLock(db, "alert-1"));

    await assertFails(deleteDoc(doc(db, "openPetAlerts", "pet-1")));
    await assertFails(publishWithLock(db, "alert-2"));
  });

  it("lock: deleting the lock once its alert is closed is allowed", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser("owner-1");
    await assertSucceeds(publishWithLock(db, "alert-1"));
    await assertSucceeds(
      updateDoc(doc(db, "alerts", "alert-1"), { status: "CLOSED", closedAtMillis: 2000000 })
    );

    await assertSucceeds(deleteDoc(doc(db, "openPetAlerts", "pet-1")));
  });

  it("create: a pet whose alert is closed can get a new one", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser("owner-1");
    await assertSucceeds(publishWithLock(db, "alert-1"));
    await assertSucceeds(
      updateDoc(doc(db, "alerts", "alert-1"), { status: "CLOSED", closedAtMillis: 2000000 })
    );

    await assertSucceeds(publishWithLock(db, "alert-2"));
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

    // The pet's lock is written with the alert, as publishAlert does
    const batch = writeBatch(db);
    batch.set(doc(db, "alerts", "alert-1"), alertData());
    batch.set(doc(db, "openPetAlerts", "pet-1"), { alertId: "alert-1", ownerId: "owner-1" });
    await assertSucceeds(batch.commit());
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

  // Circumstances of the loss (#409): optional, only the enum values, set at publication
  const LOSS_CAUSES = [
    "ESCAPED_FROM_HOME", "DURING_A_WALK", "SCARED_BY_NOISE", "DURING_MOVE_OR_TRIP", "STOLEN",
    "UNKNOWN",
  ];
  const LOSS_ENVIRONMENTS = [
    "CITY_CENTRE", "SUBURB", "VILLAGE_OR_RURAL", "FOREST_OR_MOUNTAIN", "UNKNOWN",
  ];

  /** Publishes a new alert of owner-1 with its pet's lock, as publishAlert does. */
  async function publish(alertId, petId, overrides) {
    await seedPet(petId, "owner-1");
    const db = firestoreForUser("owner-1");
    const batch = writeBatch(db);
    batch.set(doc(db, "alerts", alertId), alertData({ id: alertId, petId, ...overrides }));
    batch.set(doc(db, "openPetAlerts", petId), { alertId, ownerId: "owner-1" });
    return batch.commit();
  }

  it("loss: every allowed cause is accepted when publishing", async () => {
    // One alert and pet per value, so each write is a create
    for (const lossCause of LOSS_CAUSES) {
      await assertSucceeds(publish(`alert-cause-${lossCause}`, `pet-cause-${lossCause}`, { lossCause }));
    }
  });

  it("loss: every allowed environment is accepted when publishing", async () => {
    for (const lossEnvironment of LOSS_ENVIRONMENTS) {
      await assertSucceeds(
        publish(`alert-env-${lossEnvironment}`, `pet-env-${lossEnvironment}`, { lossEnvironment }),
      );
    }
  });

  it("loss: an alert without cause or environment (older app) is accepted", async () => {
    const legacy = alertData({ id: "alert-legacy", petId: "pet-legacy" });
    assert.ok(!("lossCause" in legacy) && !("lossEnvironment" in legacy));

    await assertSucceeds(publish("alert-legacy", "pet-legacy", {}));
  });

  it("loss: an unknown cause is denied", async () => {
    for (const [i, lossCause] of ["LOST_IN_SPACE", "stolen", "", 42, null].entries()) {
      await assertFails(publish(`alert-bad-cause-${i}`, `pet-bad-cause-${i}`, { lossCause }));
    }
  });

  it("loss: an unknown environment is denied", async () => {
    for (const [i, lossEnvironment] of ["BEACH", "suburb", "", 42, null].entries()) {
      await assertFails(
        publish(`alert-bad-env-${i}`, `pet-bad-env-${i}`, { lossEnvironment }),
      );
    }
  });

  it("loss: the owner can't change the cause or environment after publishing", async () => {
    await seedAlert({ lossCause: "DURING_A_WALK", lossEnvironment: "SUBURB" });
    const alert = doc(firestoreForUser("owner-1"), "alerts", "alert-1");

    await assertFails(updateDoc(alert, { lossCause: "STOLEN" }));
    await assertFails(updateDoc(alert, { lossEnvironment: "CITY_CENTRE" }));
  });

  it("loss: the owner can't add a cause or environment after publishing", async () => {
    await seedAlert();
    const alert = doc(firestoreForUser("owner-1"), "alerts", "alert-1");

    await assertFails(updateDoc(alert, { lossCause: "STOLEN" }));
    await assertFails(updateDoc(alert, { lossEnvironment: "SUBURB" }));
  });

  it("loss: closing an alert keeps its cause and environment", async () => {
    await seedAlert({ lossCause: "DURING_A_WALK", lossEnvironment: "SUBURB" });
    const db = firestoreForUser("owner-1");

    await assertSucceeds(updateDoc(doc(db, "alerts", "alert-1"), closing));
  });

  // Pet details copied into the alert (#403): same values as the pet's, set at publication
  it("pet details: allowed birth year, sex and size are accepted when publishing", async () => {
    await assertSucceeds(
      publish("alert-details", "pet-details", { petBirthYear: 2019, petSex: "MALE", petSize: "SMALL" }),
    );
    for (const petSex of ["MALE", "FEMALE", "UNKNOWN"]) {
      await assertSucceeds(publish(`alert-sex-${petSex}`, `pet-sex-${petSex}`, { petSex }));
    }
    for (const petSize of ["SMALL", "MEDIUM", "LARGE", "UNKNOWN"]) {
      await assertSucceeds(publish(`alert-size-${petSize}`, `pet-size-${petSize}`, { petSize }));
    }
    await assertSucceeds(publish("alert-no-year", "pet-no-year", { petBirthYear: null }));
  });

  it("pet details: unknown values or a future birth year are denied", async () => {
    const nextYear = new Date().getUTCFullYear() + 1;
    for (const [i, overrides] of [
      { petSex: "OTHER" },
      { petSex: null },
      { petSize: "huge" },
      { petSize: 3 },
      { petBirthYear: nextYear },
      { petBirthYear: "2019" },
    ].entries()) {
      await assertFails(publish(`alert-bad-details-${i}`, `pet-bad-details-${i}`, overrides));
    }
  });

  it("pet details: the owner can't change them after publishing, but can still close", async () => {
    await seedAlert({ petBirthYear: 2019, petSex: "MALE", petSize: "SMALL" });
    const db = firestoreForUser("owner-1");
    const alert = doc(db, "alerts", "alert-1");

    await assertFails(updateDoc(alert, { petBirthYear: 2018 }));
    await assertFails(updateDoc(alert, { petSex: "FEMALE" }));
    await assertFails(updateDoc(alert, { petSize: "LARGE" }));
    await assertSucceeds(updateDoc(alert, closing));
  });

  // openPetAlerts/{petId}: the lock publishAlert writes with each alert (one open alert per pet)
  it("lock: the pet's owner can read a missing lock and create one", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser("owner-1");

    await assertSucceeds(getDoc(doc(db, "openPetAlerts", "pet-1")));
    await assertSucceeds(
      setDoc(doc(db, "openPetAlerts", "pet-1"), { alertId: "alert-1", ownerId: "owner-1" })
    );
  });

  it("lock: another user cannot read or write the pet's lock", async () => {
    await seedPet("pet-1", "owner-1");
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "openPetAlerts", "pet-1"), {
        alertId: "alert-1",
        ownerId: "owner-1",
      });
    });
    const db = firestoreForUser("intruder");

    await assertFails(getDoc(doc(db, "openPetAlerts", "pet-1")));
    await assertFails(
      setDoc(doc(db, "openPetAlerts", "pet-1"), { alertId: "alert-2", ownerId: "intruder" })
    );
    await assertFails(deleteDoc(doc(db, "openPetAlerts", "pet-1")));
  });

  it("lock: invalid fields or another owner's pet are denied", async () => {
    await seedPet("pet-1", "owner-1");
    await seedPet("pet-2", "owner-2");
    const db = firestoreForUser("owner-1");

    await assertFails(
      setDoc(doc(db, "openPetAlerts", "pet-1"), { alertId: "", ownerId: "owner-1" })
    );
    await assertFails(
      setDoc(doc(db, "openPetAlerts", "pet-1"), {
        alertId: "alert-1",
        ownerId: "owner-1",
        extra: true,
      })
    );
    await assertFails(
      setDoc(doc(db, "openPetAlerts", "pet-2"), { alertId: "alert-1", ownerId: "owner-1" })
    );
  });

  it("lock: anonymous user is denied", async () => {
    await seedPet("pet-1", "owner-1");
    const db = firestoreForUser(null);

    await assertFails(getDoc(doc(db, "openPetAlerts", "pet-1")));
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

  const sightingDetails = {
    activities: ["EATING", "HIDING"],
    conditions: ["LIMPING", "THIN"],
    reactions: ["KEPT_ITS_DISTANCE", "GROWLED_OR_HISSED"],
    placeType: "INSIDE_BUILDING",
    collar: "NO_COLLAR",
  };

  const allowedDetailLists = {
    activities: [
      "WALKING", "RUNNING", "CROSSING_ROAD", "RESTING", "SLEEPING", "HIDING", "EATING",
      "DRINKING", "SCAVENGING", "HUNTING", "SNIFFING_EXPLORING", "FOLLOWING_PEOPLE",
      "PLAYING_WITH_PEOPLE", "WITH_OTHER_ANIMALS", "FIGHTING", "VOCALIZING",
    ],
    conditions: [
      "LOOKS_FINE", "INJURED", "LIMPING", "BLEEDING", "THIN", "LOOKS_SICK", "EXHAUSTED", "DIRTY",
      "WET",
    ],
    reactions: [
      "CAME_CLOSER", "FOLLOWED_ME", "LET_ITSELF_BE_TOUCHED", "ACCEPTED_FOOD", "IGNORED_ME",
      "KEPT_ITS_DISTANCE", "RAN_AWAY", "HID", "GROWLED_OR_HISSED", "SNAPPED_OR_ATTACKED",
    ],
  };

  const allowedDetailValues = {
    placeType: ["STREET", "PARK", "GARDEN", "FIELD", "FOREST", "INSIDE_BUILDING", "UNKNOWN"],
    collar: ["WORE_COLLAR", "NO_COLLAR", "UNKNOWN"],
  };

  it("sighting details: every allowed value is accepted on create", async () => {
    await seedAlert("alert-1", "owner-1");
    const db = firestoreForUser("user-1");

    // One id per value, so each write is a create (writing the same id again is an update)
    async function assertCreated(id, details) {
      await assertSucceeds(setDoc(doc(db, "sightings", id), sighting(id, "user-1", details)));
    }

    for (const [field, values] of Object.entries(allowedDetailLists)) {
      for (const value of values) {
        await assertCreated(`sighting-${field}-${value}`, { [field]: [value] });
      }
    }
    for (const [field, values] of Object.entries(allowedDetailValues)) {
      for (const value of values) {
        await assertCreated(`sighting-${field}-${value}`, { [field]: value });
      }
    }
    await assertCreated("sighting-all-details", sightingDetails);
  });

  it("sighting details: empty lists are accepted", async () => {
    await seedAlert("alert-1", "owner-1");
    const db = firestoreForUser("user-1");

    await assertSucceeds(
      setDoc(
        doc(db, "sightings", "sighting-1"),
        sighting("sighting-1", "user-1", { activities: [], conditions: [], reactions: [] }),
      ),
    );
  });

  it("sighting details: invalid values are denied", async () => {
    await seedAlert("alert-1", "owner-1");
    const ref = doc(firestoreForUser("user-1"), "sightings", "sighting-1");
    const invalidDetails = [
      { activities: ["TELEPORTING"] },
      { activities: ["eating"] },
      { activities: "EATING" },
      { activities: null },
      { activities: ["EATING", "EATING"] },
      { conditions: ["DEAD"] },
      { conditions: ["LOOKS_FINE", "LIMPING"] },
      { conditions: [1] },
      { reactions: ["DANCED"] },
      { reactions: ["HID", "HID"] },
      { placeType: "BEACH" },
      { placeType: null },
      { collar: "MAYBE" },
      { collar: true },
      // The single-value keys of the first version are not part of the schema
      { activity: "MOVING" },
    ];

    for (const details of invalidDetails) {
      await assertFails(setDoc(ref, sighting("sighting-1", "user-1", details)));
    }
  });

  it("sighting details: the reporter can change them while the sighting is pending", async () => {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("sighting-1", "user-1");
    const ref = doc(firestoreForUser("user-1"), "sightings", "sighting-1");

    await assertSucceeds(updateDoc(ref, sightingDetails));
    await assertSucceeds(updateDoc(ref, { conditions: ["LOOKS_FINE"] }));
    await assertFails(updateDoc(ref, { conditions: ["DEAD"] }));
  });

  it("sighting details: the alert owner can review, but cannot change them", async () => {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("sighting-1", "user-1", sightingDetails);
    const ref = doc(firestoreForUser("owner-1"), "sightings", "sighting-1");

    await assertSucceeds(updateDoc(ref, { status: "DISMISSED" }));
    await assertFails(updateDoc(ref, { conditions: ["LOOKS_FINE"] }));
    await assertFails(updateDoc(ref, { status: "DISMISSED", reactions: ["CAME_CLOSER"] }));
    await assertFails(updateDoc(ref, { collar: "WORE_COLLAR" }));
    await assertFails(updateDoc(ref, { activities: deleteField() }));
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

    await assertSucceeds(updateDoc(ref, { status: "DISMISSED" }));
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
      const operation = updateDoc(doc(db, "sightings", "sighting-1"), { status: "DISMISSED" });

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

describe("Firestore trust access rules", () => {
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

  const seedTrust = (uid, score, judged) =>
    testEnvironment.withSecurityRulesDisabled((context) =>
      setDoc(doc(context.firestore(), "trust", uid), { score, judged }),
    );

  it("trust read: any signed-in user is allowed, signed-out users are denied", async () => {
    await seedTrust("helper-1", 0.6, 5);

    const ownRead = await assertSucceeds(getDoc(doc(firestoreForUser("helper-1"), "trust", "helper-1")));
    const ownerRead = await assertSucceeds(getDoc(doc(firestoreForUser("owner-1"), "trust", "helper-1")));
    assert.equal(ownRead.data().score, 0.6);
    assert.equal(ownerRead.data().judged, 5);
    await assertFails(getDoc(doc(firestoreForUser(null), "trust", "helper-1")));
  });

  it("trust read: a user without a document can be read as missing", async () => {
    const missing = await assertSucceeds(getDoc(doc(firestoreForUser("owner-1"), "trust", "helper-2")));
    assert.equal(missing.exists(), false);
  });

  it("trust list: queries on the collection are denied", async () => {
    await seedTrust("helper-1", 0.6, 5);

    await assertFails(getDocs(collection(firestoreForUser("owner-1"), "trust")));
  });

  it("trust write: no client may write a trust document without judging a sighting", async () => {
    await seedTrust("helper-1", 0.3, 0);

    for (const userId of ["helper-1", "owner-1"]) {
      const db = firestoreForUser(userId);
      await assertFails(setDoc(doc(db, "trust", "helper-1"), { score: 1, judged: 10 }));
      await assertFails(updateDoc(doc(db, "trust", "helper-1"), { score: 1 }));
      await assertFails(deleteDoc(doc(db, "trust", "helper-1")));
      await assertFails(setDoc(doc(db, "trust", userId + "-new"), { score: 1, judged: 10 }));
    }
  });

  // The path of TrustRepositoryFirestore.applyVerdict: the owner of alert-1 judges sighting-1,
  // reported by helper-1, and applies the verdict to helper-1's trust in the same write.

  const confirmedFromInitial = 0.3 + 0.1 * (1 - 0.3);

  const seedAlert = (id, ownerId) =>
    testEnvironment.withSecurityRulesDisabled((context) =>
      setDoc(doc(context.firestore(), "alerts", id), { id, ownerId, status: "OPEN" }),
    );

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

  const seedSighting = (id, reporterId, overrides = {}) =>
    testEnvironment.withSecurityRulesDisabled((context) =>
      setDoc(doc(context.firestore(), "sightings", id), sighting(id, reporterId, overrides)),
    );

  async function seedJudgeableSighting() {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("sighting-1", "helper-1");
  }

  /**
   * Judges sightingId as verdict and writes trust to the trust of reporterId, in one write. The
   * same write sets the sighting's status, like confirmSighting; a null status leaves it as it is.
   */
  function judge(
    userId,
    { sightingId = "sighting-1", reporterId = "helper-1", verdict, trust, status = "CONFIRMED" },
  ) {
    const db = firestoreForUser(userId);
    const batch = writeBatch(db);
    batch.set(doc(db, "trust", reporterId), { lastSightingId: sightingId, ...trust });
    const sightingChange =
      status === null ? { trustVerdict: verdict } : { trustVerdict: verdict, status };
    batch.update(doc(db, "sightings", sightingId), sightingChange);
    return batch.commit();
  }

  it("trust verdict: the alert owner may apply a confirmed verdict to a new reporter", async () => {
    await seedJudgeableSighting();

    await assertSucceeds(
      judge("owner-1", { verdict: "CONFIRMED", trust: { score: confirmedFromInitial, judged: 1 } }),
    );
  });

  it("trust verdict: a confirmed verdict moves a stored trust", async () => {
    await seedJudgeableSighting();
    await seedTrust("helper-1", 0.6, 5);

    await assertSucceeds(
      judge("owner-1", { verdict: "CONFIRMED", trust: { score: 0.6 + 0.1 * (1 - 0.6), judged: 6 } }),
    );
  });

  // #384: in S6 only confirmations move the score, so one owner cannot lower a reporter's score
  it("trust verdict: a false verdict is denied, whatever the status", async () => {
    await seedJudgeableSighting();
    await seedTrust("helper-1", 0.6, 5);

    for (const status of ["CONFIRMED", "DISMISSED", null]) {
      await assertFails(
        judge("owner-1", { verdict: "FALSE", status, trust: { score: 0.3, judged: 6 } }),
      );
    }
  });

  it("trust verdict: a verdict that does not confirm the sighting is denied", async () => {
    await seedJudgeableSighting();
    const trust = { score: confirmedFromInitial, judged: 1 };

    for (const status of ["DISMISSED", "PENDING", null]) {
      await assertFails(judge("owner-1", { verdict: "CONFIRMED", status, trust }));
    }
  });

  it("trust verdict: a dismissed sighting cannot be credited without confirming it", async () => {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("sighting-1", "helper-1", { status: "DISMISSED" });
    const trust = { score: confirmedFromInitial, judged: 1 };

    await assertFails(judge("owner-1", { verdict: "CONFIRMED", status: null, trust }));
    await assertSucceeds(judge("owner-1", { verdict: "CONFIRMED", trust }));
  });

  it("trust verdict: a sighting with every animal detail can be judged", async () => {
    await seedAlert("alert-1", "owner-1");
    // The largest sighting the app writes: every list full, so validSighting does the most work
    await seedSighting("sighting-1", "helper-1", {
      headingDegrees: 359,
      photoUrl: "https://example.com/photo.jpg",
      status: "CONFIRMED",
      activities: [
        "WALKING", "RUNNING", "CROSSING_ROAD", "RESTING", "SLEEPING", "HIDING", "EATING",
        "DRINKING", "SCAVENGING", "HUNTING", "SNIFFING_EXPLORING", "FOLLOWING_PEOPLE",
        "PLAYING_WITH_PEOPLE", "WITH_OTHER_ANIMALS", "FIGHTING", "VOCALIZING",
      ],
      conditions: ["INJURED", "LIMPING", "BLEEDING", "THIN", "LOOKS_SICK", "EXHAUSTED", "DIRTY", "WET"],
      reactions: [
        "CAME_CLOSER", "FOLLOWED_ME", "LET_ITSELF_BE_TOUCHED", "ACCEPTED_FOOD", "IGNORED_ME",
        "KEPT_ITS_DISTANCE", "RAN_AWAY", "HID", "GROWLED_OR_HISSED", "SNAPPED_OR_ATTACKED",
      ],
      placeType: "INSIDE_BUILDING",
      collar: "NO_COLLAR",
    });

    await assertSucceeds(
      judge("owner-1", { verdict: "CONFIRMED", trust: { score: confirmedFromInitial, judged: 1 } }),
    );
  });

  it("trust verdict: a score or judged count other than one verdict gives is denied", async () => {
    await seedJudgeableSighting();
    await seedTrust("helper-1", 0.6, 5);

    for (const trust of [
      { score: 0.64, judged: 7 },
      { score: 0.64, judged: 5 },
      { score: 1, judged: 6 },
      // What a false verdict would give, sent with a confirmed one
      { score: 0.3, judged: 6 },
    ]) {
      await assertFails(judge("owner-1", { verdict: "CONFIRMED", trust }));
    }
  });

  it("trust verdict: only the owner of the sighting's alert may apply it", async () => {
    await seedJudgeableSighting();
    const trust = { score: confirmedFromInitial, judged: 1 };

    for (const userId of ["helper-1", "stranger-1", null]) {
      await assertFails(judge(userId, { verdict: "CONFIRMED", trust }));
    }
  });

  it("trust verdict: a sighting already judged cannot be counted again", async () => {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("sighting-1", "helper-1", { trustVerdict: "CONFIRMED" });
    await seedTrust("helper-1", confirmedFromInitial, 1);

    await assertFails(
      judge("owner-1", {
        verdict: "CONFIRMED",
        trust: { score: confirmedFromInitial + 0.1 * (1 - confirmedFromInitial), judged: 2 },
      }),
    );
  });

  it("trust verdict: the trust and the sighting must be written together", async () => {
    await seedJudgeableSighting();
    const db = firestoreForUser("owner-1");

    await assertFails(
      setDoc(doc(db, "trust", "helper-1"), {
        score: confirmedFromInitial,
        judged: 1,
        lastSightingId: "sighting-1",
      }),
    );
    await assertFails(updateDoc(doc(db, "sightings", "sighting-1"), { trustVerdict: "CONFIRMED" }));
  });

  it("trust verdict: a sighting only moves the trust of its own reporter", async () => {
    await seedJudgeableSighting();

    await assertFails(
      judge("owner-1", {
        reporterId: "helper-2",
        verdict: "CONFIRMED",
        trust: { score: confirmedFromInitial, judged: 1 },
      }),
    );
  });

  it("trust verdict: an unknown verdict is denied", async () => {
    await seedJudgeableSighting();

    await assertFails(
      judge("owner-1", { verdict: "MAYBE", trust: { score: 0.15, judged: 1 } }),
    );
  });

  it("trust verdict: the reporter cannot edit or delete a judged sighting", async () => {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("sighting-1", "helper-1", { trustVerdict: "FALSE" });
    const db = firestoreForUser("helper-1");

    await assertFails(setDoc(doc(db, "sightings", "sighting-1"), sighting("sighting-1", "helper-1")));
    await assertFails(updateDoc(doc(db, "sightings", "sighting-1"), { seenAtMillis: 1000001 }));
    await assertFails(deleteDoc(doc(db, "sightings", "sighting-1")));
  });

  it("trust verdict: a reporter cannot create or edit a sighting to look judged", async () => {
    await seedAlert("alert-1", "owner-1");
    const db = firestoreForUser("helper-1");

    await assertFails(
      setDoc(
        doc(db, "sightings", "sighting-1"),
        sighting("sighting-1", "helper-1", { trustVerdict: "CONFIRMED" }),
      ),
    );
    await assertSucceeds(setDoc(doc(db, "sightings", "sighting-1"), sighting("sighting-1", "helper-1")));
    await assertFails(updateDoc(doc(db, "sightings", "sighting-1"), { trustVerdict: "CONFIRMED" }));
  });

  it("trust verdict: the owner may still change the status of a judged sighting", async () => {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("sighting-1", "helper-1", { trustVerdict: "CONFIRMED" });
    const db = firestoreForUser("owner-1");

    await assertSucceeds(updateDoc(doc(db, "sightings", "sighting-1"), { status: "DISMISSED" }));
    await assertFails(updateDoc(doc(db, "sightings", "sighting-1"), { trustVerdict: "FALSE" }));
  });

  // The path of TrustRepositoryFirestore.confirmSighting: the status and the verdict together.

  it("trust confirm: the alert owner may confirm and judge a sighting in one write", async () => {
    await seedJudgeableSighting();

    await assertSucceeds(
      judge("owner-1", {
        verdict: "CONFIRMED",
        status: "CONFIRMED",
        trust: { score: confirmedFromInitial, judged: 1 },
      }),
    );
  });

  it("trust confirm: the status and the verdict must agree", async () => {
    await seedJudgeableSighting();

    for (const [status, verdict] of [
      ["DISMISSED", "CONFIRMED"],
      ["CONFIRMED", "FALSE"],
    ]) {
      const score = verdict === "CONFIRMED" ? confirmedFromInitial : 0.15;
      await assertFails(judge("owner-1", { verdict, status, trust: { score, judged: 1 } }));
    }
  });

  it("trust confirm: a sighting with every animal detail can be confirmed", async () => {
    await seedAlert("alert-1", "owner-1");
    // The largest sighting the app writes, so the rules stay under their expression limit
    await seedSighting("sighting-1", "helper-1", {
      headingDegrees: 359,
      photoUrl: "https://example.com/photo.jpg",
      activities: [
        "WALKING", "RUNNING", "CROSSING_ROAD", "RESTING", "SLEEPING", "HIDING", "EATING",
        "DRINKING", "SCAVENGING", "HUNTING", "SNIFFING_EXPLORING", "FOLLOWING_PEOPLE",
        "PLAYING_WITH_PEOPLE", "WITH_OTHER_ANIMALS", "FIGHTING", "VOCALIZING",
      ],
      conditions: ["INJURED", "LIMPING", "BLEEDING", "THIN", "LOOKS_SICK", "EXHAUSTED", "DIRTY", "WET"],
      reactions: [
        "CAME_CLOSER", "FOLLOWED_ME", "LET_ITSELF_BE_TOUCHED", "ACCEPTED_FOOD", "IGNORED_ME",
        "KEPT_ITS_DISTANCE", "RAN_AWAY", "HID", "GROWLED_OR_HISSED", "SNAPPED_OR_ATTACKED",
      ],
      placeType: "INSIDE_BUILDING",
      collar: "NO_COLLAR",
    });

    await assertSucceeds(
      judge("owner-1", {
        verdict: "CONFIRMED",
        status: "CONFIRMED",
        trust: { score: confirmedFromInitial, judged: 1 },
      }),
    );
  });

  it("trust confirm: a first confirmation without its verdict is denied", async () => {
    await seedJudgeableSighting();
    const ref = doc(firestoreForUser("owner-1"), "sightings", "sighting-1");

    // It would show as confirmed without moving the reporter's trust
    await assertFails(updateDoc(ref, { status: "CONFIRMED" }));
    await assertSucceeds(updateDoc(ref, { status: "DISMISSED" }));
  });

  it("trust confirm: a judged sighting may be confirmed again without its trust", async () => {
    await seedAlert("alert-1", "owner-1");
    await seedSighting("sighting-1", "helper-1", {
      status: "DISMISSED",
      trustVerdict: "CONFIRMED",
    });
    const ref = doc(firestoreForUser("owner-1"), "sightings", "sighting-1");

    await assertSucceeds(updateDoc(ref, { status: "CONFIRMED" }));
    await assertFails(updateDoc(ref, { status: "CONFIRMED", trustVerdict: "FALSE" }));
  });
});

describe("Firestore block list rules", () => {
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

  const blockDoc = (db, userId, blockedUid) => doc(db, "users", userId, "blocks", blockedUid);

  const seedBlock = (userId, blockedUid) =>
    testEnvironment.withSecurityRulesDisabled((context) =>
      setDoc(blockDoc(context.firestore(), userId, blockedUid), { blockedUid }),
    );

  it("block: a user can block someone in their own list, and block them again", async () => {
    const db = firestoreForUser("alice");

    await assertSucceeds(setDoc(blockDoc(db, "alice", "spammer"), { blockedUid: "spammer" }));
    await assertSucceeds(setDoc(blockDoc(db, "alice", "spammer"), { blockedUid: "spammer" }));
  });

  it("block: writing in someone else's list, or signed out, is denied", async () => {
    await assertFails(
      setDoc(blockDoc(firestoreForUser("bob"), "alice", "spammer"), { blockedUid: "spammer" }),
    );
    await assertFails(
      setDoc(blockDoc(firestoreForUser(null), "alice", "spammer"), { blockedUid: "spammer" }),
    );
  });

  it("block: blocking yourself, a wrong uid or extra fields are denied", async () => {
    const db = firestoreForUser("alice");

    await assertFails(setDoc(blockDoc(db, "alice", "alice"), { blockedUid: "alice" }));
    await assertFails(setDoc(blockDoc(db, "alice", "spammer"), { blockedUid: "someone-else" }));
    await assertFails(
      setDoc(blockDoc(db, "alice", "spammer"), { blockedUid: "spammer", reason: "rude" }),
    );
  });

  it("block list read: only its owner can read or list it", async () => {
    await seedBlock("alice", "spammer");

    const ownList = await assertSucceeds(
      getDocs(collection(firestoreForUser("alice"), "users", "alice", "blocks")),
    );
    assert.deepEqual(ownList.docs.map((d) => d.id), ["spammer"]);
    await assertSucceeds(getDoc(blockDoc(firestoreForUser("alice"), "alice", "spammer")));

    for (const userId of ["bob", "spammer", null]) {
      const db = firestoreForUser(userId);
      await assertFails(getDoc(blockDoc(db, "alice", "spammer")));
      await assertFails(getDocs(collection(db, "users", "alice", "blocks")));
    }
  });

  it("unblock: only the owner can delete from their list", async () => {
    await seedBlock("alice", "spammer");

    await assertFails(deleteDoc(blockDoc(firestoreForUser("bob"), "alice", "spammer")));
    await assertFails(deleteDoc(blockDoc(firestoreForUser("spammer"), "alice", "spammer")));
    await assertFails(deleteDoc(blockDoc(firestoreForUser(null), "alice", "spammer")));
    await assertSucceeds(deleteDoc(blockDoc(firestoreForUser("alice"), "alice", "spammer")));
  });
});

describe("Firestore vote rules", () => {
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

  const HOUR = 60 * 60 * 1000;
  const DAY = 24 * HOUR;
  const daysAgo = (days) => Timestamp.fromMillis(Date.now() - days * DAY);
  /** [count] vote times, one per hour, the latest one hour ago (oldest first). */
  const hourlyVotes = (count) =>
    Array.from({ length: count }, (_, i) => Timestamp.fromMillis(Date.now() - (count - i) * HOUR));

  function firestoreForUser(userId) {
    return userId === null
      ? testEnvironment.unauthenticatedContext().firestore()
      : testEnvironment.authenticatedContext(userId).firestore();
  }

  const seed = (documentPath, data) =>
    testEnvironment.withSecurityRulesDisabled((context) =>
      setDoc(doc(context.firestore(), documentPath), data),
    );

  /**
   * sighting-1 by reporter-1 on alert-1 (owned by owner-1), and a voter who meets every condition
   * unless overridden: an account 30 days old, Trusted (score 0.5, 3 judged sightings).
   */
  async function seedEligibleVoter(
    voterId,
    { createdAt = daysAgo(30), score = 0.5, judged = 3, trust = true, user = true } = {},
  ) {
    await seed("alerts/alert-1", { id: "alert-1", ownerId: "owner-1", status: "OPEN" });
    await seed("sightings/sighting-1", {
      id: "sighting-1",
      reporterId: "reporter-1",
      location: { latitude: 46.5, longitude: 6.6 },
      seenAtMillis: 1000000,
      species: "DOG",
      alertId: "alert-1",
      status: "PENDING",
    });
    if (user) {
      const profile = {
        uid: voterId,
        name: "Voter",
        phone: "",
        email: "voter@example.com",
        homeArea: "",
      };
      await seed(`users/${voterId}`, createdAt === null ? profile : { ...profile, createdAt });
    }
    if (trust) {
      await seed(`trust/${voterId}`, { score, judged, lastSightingId: "old-sighting" });
    }
  }

  /** Seeds the voter's quota with votes at these [times] (oldest first). */
  const seedQuota = (voterId, times) =>
    seed(`voteQuota/${voterId}`, {
      recent: times.slice(0, -1),
      last: times[times.length - 1],
      lastSightingId: "older-sighting",
    });

  /**
   * The write of VoteRepositoryFirestore.castVote: the vote and the voter's quota, in one batch.
   * [overrides] change the vote document, [quotaOverrides] the quota document.
   */
  async function castVote(
    userId,
    {
      sightingId = "sighting-1",
      voterId = userId,
      overrides = {},
      quotaOverrides = {},
      withQuota = true,
    } = {},
  ) {
    let before = [];
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      const quota = await getDoc(doc(context.firestore(), "voteQuota", voterId));
      if (quota.exists()) before = [...quota.data().recent, quota.data().last];
    });
    const db = firestoreForUser(userId);
    const batch = writeBatch(db);
    batch.set(doc(db, "sightings", sightingId, "votes", voterId), {
      voterId,
      votedAt: serverTimestamp(),
      ...overrides,
    });
    if (withQuota) {
      batch.set(doc(db, "voteQuota", voterId), {
        recent: before.slice(-9),
        last: serverTimestamp(),
        lastSightingId: sightingId,
        ...quotaOverrides,
      });
    }
    return batch.commit();
  }

  it("vote create: an eligible voter can vote on someone else's sighting", async () => {
    await seedEligibleVoter("voter-1");

    await assertSucceeds(castVote("voter-1"));
  });

  it("vote create: the reporter cannot vote on their own sighting", async () => {
    await seedEligibleVoter("reporter-1");

    await assertFails(castVote("reporter-1"));
  });

  it("vote create: a vote on a sighting that does not exist is denied", async () => {
    await seedEligibleVoter("voter-1");

    await assertFails(castVote("voter-1", { sightingId: "missing-sighting" }));
  });

  it("vote create: only your own vote (document id and voterId are your uid)", async () => {
    await seedEligibleVoter("voter-1");
    await seedEligibleVoter("voter-2");

    await assertFails(castVote("voter-1", { voterId: "voter-2" }));
    await assertFails(castVote("voter-1", { overrides: { voterId: "voter-2" } }));
    await assertFails(castVote(null, { voterId: "voter-1" }));
  });

  it("vote: one per user per sighting, never changed or deleted", async () => {
    await seedEligibleVoter("voter-1");
    await assertSucceeds(castVote("voter-1"));
    const vote = doc(firestoreForUser("voter-1"), "sightings/sighting-1/votes/voter-1");

    await assertFails(castVote("voter-1"));
    await assertFails(updateDoc(vote, { votedAt: serverTimestamp() }));
    await assertFails(deleteDoc(vote));
  });

  it("vote create: the app cannot send a weight or any other field", async () => {
    await seedEligibleVoter("voter-1");

    await assertFails(castVote("voter-1", { overrides: { weight: 1 } }));
    await assertFails(castVote("voter-1", { overrides: { status: "VOTED_FALSE" } }));
  });

  it("vote create: votedAt must be the server time", async () => {
    await seedEligibleVoter("voter-1");

    await assertFails(castVote("voter-1", { overrides: { votedAt: daysAgo(1) } }));
  });

  it("vote create: an account 8 days old is allowed, 6 days old is denied", async () => {
    await seedEligibleVoter("old-voter", { createdAt: daysAgo(8) });
    await seedEligibleVoter("new-voter", { createdAt: daysAgo(6) });

    await assertSucceeds(castVote("old-voter"));
    await assertFails(castVote("new-voter"));
  });

  it("vote create: an account without createdAt or without a profile is denied", async () => {
    await seedEligibleVoter("no-date", { createdAt: null });
    await seedEligibleVoter("no-profile", { user: false });

    await assertFails(castVote("no-date"));
    await assertFails(castVote("no-profile"));
  });

  it("vote create: Trusted at the lowest score (0.4, 3 judged) is allowed", async () => {
    await seedEligibleVoter("voter-1", { score: 0.4, judged: 3 });

    await assertSucceeds(castVote("voter-1"));
  });

  it("vote create: a score below Trusted is denied", async () => {
    await seedEligibleVoter("voter-1", { score: 0.39, judged: 10 });

    await assertFails(castVote("voter-1"));
  });

  it("vote create: fewer than 3 judged sightings (level New) is denied", async () => {
    await seedEligibleVoter("voter-1", { score: 0.9, judged: 2 });

    await assertFails(castVote("voter-1"));
  });

  it("vote create: a user without a trust score is denied", async () => {
    await seedEligibleVoter("voter-1", { trust: false });

    await assertFails(castVote("voter-1"));
  });

  it("vote create: the 10th vote in 24 hours is allowed", async () => {
    await seedEligibleVoter("voter-1");
    await seedQuota("voter-1", hourlyVotes(9));

    await assertSucceeds(castVote("voter-1"));
  });

  it("vote create: an 11th vote in 24 hours is denied", async () => {
    await seedEligibleVoter("voter-1");
    await seedQuota("voter-1", hourlyVotes(10));

    await assertFails(castVote("voter-1"));
  });

  it("vote create: allowed again once the oldest of the last 10 votes is over 24 hours old", async () => {
    await seedEligibleVoter("voter-1");
    const times = hourlyVotes(10);
    times[0] = Timestamp.fromMillis(Date.now() - 25 * HOUR);
    await seedQuota("voter-1", times);

    await assertSucceeds(castVote("voter-1"));
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      const quota = (await getDoc(doc(context.firestore(), "voteQuota/voter-1"))).data();
      assert.equal(quota.recent.length, 9);
      assert.ok(quota.recent[0].isEqual(times[1]));
    });
  });

  it("vote create: a vote that does not update the quota is denied", async () => {
    await seedEligibleVoter("voter-1");

    await assertFails(castVote("voter-1", { withQuota: false }));
  });

  it("quota: cannot be reset, changed or deleted, and only its owner can read it", async () => {
    await seedEligibleVoter("voter-1");
    await seedQuota("voter-1", hourlyVotes(10));
    const quota = doc(firestoreForUser("voter-1"), "voteQuota", "voter-1");

    // Reset in the same write as a vote, then without a vote
    await assertFails(castVote("voter-1", { quotaOverrides: { recent: [] } }));
    await assertFails(
      setDoc(quota, { recent: [], last: serverTimestamp(), lastSightingId: "sighting-1" }),
    );
    await assertFails(deleteDoc(quota));
    await assertFails(getDoc(doc(firestoreForUser("voter-2"), "voteQuota", "voter-1")));
    await assertSucceeds(getDoc(quota));
  });

  it("vote read: signed-in users can read and count votes, signed-out users cannot", async () => {
    await seedEligibleVoter("voter-1");
    await assertSucceeds(castVote("voter-1"));
    const votes = (userId) => collection(firestoreForUser(userId), "sightings/sighting-1/votes");

    const snapshot = await assertSucceeds(getDocs(votes("owner-1")));
    assert.equal(snapshot.size, 1);
    const count = await assertSucceeds(getCountFromServer(votes("reporter-1")));
    assert.equal(count.data().count, 1);
    await assertFails(getDocs(votes(null)));
  });

  it("sighting status: nobody can set VOTED_FALSE directly, not even the alert owner", async () => {
    await seedEligibleVoter("voter-1");

    for (const userId of ["owner-1", "reporter-1", "voter-1"]) {
      await assertFails(
        updateDoc(doc(firestoreForUser(userId), "sightings", "sighting-1"), {
          status: "VOTED_FALSE",
        }),
      );
    }
  });
});
