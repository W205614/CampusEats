-- The lesson seed has a plaintext password; the application compares MD5 hashes.
UPDATE employee SET password = MD5('123456') WHERE username = 'admin';
-- Historical lesson OSS images return HTTP 403. Explicit local placeholders.
UPDATE dish SET image = '/demo-images/dish.svg';
