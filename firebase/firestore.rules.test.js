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
      { species: deleteField() },
    ];

    for (const invalidChange of invalidChanges) {
      await assertFails(updateDoc(ref, invalidChange));
    }
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
  ];

  for (const testCase of boundaryCases) {
    const shown = JSON.stringify(testCase.value);
    const label = shown.length > 40 ? `${shown.length - 2} characters` : shown;
    it(`sighting create: ${testCase.field} ${label} ${testCase.allowed ? "is allowed" : "is denied"}`, async () => {
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
    await seedPhoto("pet-1", "user-1");

    await assertFails(deleteDoc(doc(firestoreForUser("user-2"), "photos", "pet-1")));
    await assertFails(deleteDoc(doc(firestoreForUser(null), "photos", "pet-1")));
    await assertSucceeds(deleteDoc(doc(firestoreForUser("user-1"), "photos", "pet-1")));
  });
});
