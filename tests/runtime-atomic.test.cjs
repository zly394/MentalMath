const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { resolve } = require("node:path");
const { test } = require("node:test");
const { runInNewContext } = require("node:vm");

test("built Compose AtomicInt supports compare-and-set with nested call arguments", () => {
  const path = resolve(__dirname, "..", "build", "js", "packages",
    "MentalMath-composeApp", "kotlin", "compose-multiplatform-core-compose-runtime-runtime.js");
  const source = readFileSync(path, "utf8");
  const start = source.indexOf("function AtomicReference(value) {");
  const end = source.indexOf("function trackWrite(", start);
  assert.ok(start >= 0 && end > start, "Build jsBrowserDistribution before running this test");
  const context = {
    protoOf: constructor => constructor.prototype,
    equals: (left, right) => left === right
  };
  runInNewContext(source.slice(start, end), context);
  const atomic = new context.AtomicInt(0);
  const methods = Object.values(context.AtomicInt.prototype);
  const compareAndSet = methods.find(method => method.length === 2);
  const get = methods.find(method => method.length === 0);
  assert.ok(compareAndSet, "CAS must not be lowered to a one-argument setter");
  assert.equal(compareAndSet.call(atomic, 0, 1), true);
  assert.equal(get.call(atomic), 1);
  assert.equal(compareAndSet.call(atomic, 0, 2), false);
  assert.equal(get.call(atomic), 1);
});

test("snapshot notifications start once and deliver asynchronous writes without input", () => {
  const source = readFileSync(resolve(__dirname, "..", "build", "js", "packages",
    "MentalMath-composeApp", "kotlin", "compose-multiplatform-core-compose-ui-ui.js"), "utf8");
  const start = source.indexOf("function GlobalSnapshotManager$ensureStarted$lambda(");
  const end = source.indexOf("var GlobalSnapshotManager_instance;", start);
  assert.ok(start >= 0 && end > start);
  const manager = source.slice(start, end);
  const method = manager.match(/protoOf\(GlobalSnapshotManager\)\.([\w$]+) = function/)[1];
  const pendingField = manager.match(/GlobalSnapshotManager_instance\.([\w$]+);/)[1];
  let launches = 0;
  let registrations = 0;
  let deliveries = 0;
  let observer;
  const channel = {
    r18() { deliveries++; }
  };
  const companion = manager.match(/var tmp_0 = ([\w$]+);/)[1];
  const registerMethod = manager.match(/tmp_0\.([\w$]+)\(GlobalSnapshotManager/)[1];
  const context = {
    protoOf: constructor => constructor.prototype,
    equals: (left, right) => left === right,
    Channel: () => channel,
    CoroutineScope: dispatcher => dispatcher,
    get_GlobalSnapshotManagerDispatcher: () => ({}),
    launch: () => { launches++; },
    VOID: undefined,
    Unit_instance: {},
    GlobalSnapshotManager$ensureStarted$slambda_0: () => ({}),
    [companion]: {
      [registerMethod](callback) { registrations++; observer = callback; }
    }
  };
  runInNewContext(manager + "\nvar GlobalSnapshotManager_instance = new GlobalSnapshotManager();", context);
  const instance = context.GlobalSnapshotManager_instance;
  instance[method]();
  instance[method]();
  assert.equal(launches, 1);
  assert.equal(registrations, 1);
  observer();
  observer();
  assert.equal(deliveries, 1, "writes must coalesce until the notification is processed");
  instance[pendingField] = 0;
  observer();
  assert.equal(deliveries, 2, "later asynchronous writes must schedule another notification");
});
