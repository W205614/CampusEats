-- IDs 46..67 correspond to course image resources 1..22.
-- Do not overwrite images uploaded or selected by the user.
UPDATE dish SET image = CONCAT('/demo-images/', id - 45, '.png')
WHERE id BETWEEN 46 AND 67 AND image = '/demo-images/dish.svg';
