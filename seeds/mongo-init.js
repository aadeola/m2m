try {
  rs.status().ok;
} catch (e) {
  rs.initiate({ _id: 'rs0', members: [{ _id: 0, host: 'mongo:27017' }] });
}

// rs.initiate() only starts the election; it does not block until this node
// becomes PRIMARY. Running createCollection/collMod before that finishes
// fails with "not primary" — and on a fresh volume, mongosh silently prints
// the error to mongo-init's own container log and exits, leaving the
// collection with NO validator at all (schema validation failures can then
// never reproduce here).
let isPrimary = false;
for (let i = 0; i < 30 && !isPrimary; i++) {
  isPrimary = db.hello().isWritablePrimary === true;
  if (!isPrimary) sleep(1000);
}
if (!isPrimary) {
  throw new Error('mongo-init: node never became PRIMARY after rs.initiate() — aborting');
}

const mydb = db.getSiblingDB('mydb');

const orderValidator = {
  $jsonSchema: {
    bsonType: 'object',
    required: ['lineItems'],
    properties: {
      lineItems: {
        bsonType: 'array',
        items: {
          bsonType: 'object',
          required: ['product'],
          properties: {
            product: { bsonType: 'object' },
          },
        },
      },
    },
  },
};

if (mydb.getCollectionNames().includes('orders')) {
  mydb.runCommand({
    collMod: 'orders',
    validator: orderValidator,
    validationAction: 'error',
  });
} else {
  mydb.createCollection('orders', {
    validator: orderValidator,
    validationAction: 'error',
  });
}

const customerQualityValidator = {
  $jsonSchema: {
    bsonType: 'object',
    required: ['firstName', 'lastName', 'email', 'accountNumber'],
    properties: {
      firstName: { bsonType: 'string', minLength: 2, pattern: '^[A-Za-z]+([ -][A-Za-z]+)*$' },
      lastName: { bsonType: 'string', minLength: 2, pattern: '^[A-Za-z]+([ -][A-Za-z]+)*$' },
      email: { bsonType: 'string', pattern: '^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$' },
      accountNumber: { bsonType: 'string', pattern: '^[A-Za-z]{3}[0-9]{4}$' },
      phoneNumber: { bsonType: 'string', pattern: '^[0-9]{10}$' },
      invalidFields: { bsonType: 'array', items: { bsonType: 'string' } },
    },
  },
};

if (mydb.getCollectionNames().includes('customers')) {
  mydb.runCommand({
    collMod: 'customers',
    validator: customerQualityValidator,
    validationAction: 'warn',
  });
} else {
  mydb.createCollection('customers', {
    validator: customerQualityValidator,
    validationAction: 'warn',
  });
}
