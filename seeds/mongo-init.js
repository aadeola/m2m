try {
  rs.status().ok;
} catch (e) {
  rs.initiate({ _id: 'rs0', members: [{ _id: 0, host: 'mongo:27017' }] });
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
