# Add AlertRepository and test fake

## Description

Introduce the `AlertRepository` contract for publishing alerts and retrieving
open alerts near a location. Nearby results use exact distance filtering,
include alerts on the radius boundary, and are ordered newest-first with alert
ID as a deterministic tie-breaker.

Add an in-memory `FakeAlertRepository` under the test source set for use in
ViewModel tests. The fake provides deterministic IDs, injectable failures, and
access to published alerts for assertions. Share the Haversine distance
calculation through `Location.distanceKm` and cover the repository fake and
distance helper with unit tests.
