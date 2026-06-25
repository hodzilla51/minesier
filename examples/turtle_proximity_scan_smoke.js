// Smoke test for the Proximity Sensor top module.
// Equip a Proximity Sensor Module in the Top slot, then run this program.

print("proximity scan smoke");
print("fuel before:", turtle.getFuelLevel());

var results = turtle.scan();
print("results:", results.length);

var limit = Math.min(results.length, 20);
for (var i = 0; i < limit; i++) {
  var r = results[i];
  print(i + 1, r.x, r.y, r.z, r.block);
}

if (results.length > limit) {
  print("...and", results.length - limit, "more");
}

print("fuel after:", turtle.getFuelLevel());
print("done");
