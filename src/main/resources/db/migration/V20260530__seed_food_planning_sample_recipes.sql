WITH inserted_recipes AS (
    INSERT INTO recipes (name, type, instructions, base_servings, cost)
    VALUES
        (
            'Ahjus küpsetatud kana köögiviljadega',
            'PRAAD',
            'Maitsesta kana soola, pipra ja ürtidega. Lisa ahjuvormi kartul, porgand ja sibul. Küpseta 200 kraadi juures umbes 40 minutit, kuni kana on küps ja köögiviljad pehmed.',
            4,
            14.90
        ),
        (
            'Õunakook vaniljekastmega',
            'MAGUSTOIT',
            'Sega tainas, lisa õunad ja küpseta kuldseks. Serveeri jahtunult vaniljekastmega. Soovi korral lisa kaneeli ja veidi jäätist.',
            6,
            8.50
        )
    RETURNING id, name
)
INSERT INTO ingredients (recipe_id, name, base_amount, unit)
SELECT r.id, v.name, v.base_amount, v.unit
FROM inserted_recipes r
JOIN (
    VALUES
        ('Ahjus küpsetatud kana köögiviljadega', 'Kanafilee', 700.0, 'g'),
        ('Ahjus küpsetatud kana köögiviljadega', 'Kartul', 600.0, 'g'),
        ('Ahjus küpsetatud kana köögiviljadega', 'Porgand', 200.0, 'g'),
        ('Ahjus küpsetatud kana köögiviljadega', 'Sibul', 1.0, 'tk'),
        ('Ahjus küpsetatud kana köögiviljadega', 'Oliiviõli', 2.0, 'sl'),
        ('Õunakook vaniljekastmega', 'Õun', 5.0, 'tk'),
        ('Õunakook vaniljekastmega', 'Muna', 3.0, 'tk'),
        ('Õunakook vaniljekastmega', 'Jahu', 300.0, 'g'),
        ('Õunakook vaniljekastmega', 'Suhkur', 150.0, 'g'),
        ('Õunakook vaniljekastmega', 'Või', 100.0, 'g')
) AS v(recipe_name, name, base_amount, unit) ON v.recipe_name = r.name;

INSERT INTO food_calendar (date, recipe_id)
SELECT DATE '2026-05-29', id
FROM recipes
WHERE name = 'Ahjus küpsetatud kana köögiviljadega'
LIMIT 1;
