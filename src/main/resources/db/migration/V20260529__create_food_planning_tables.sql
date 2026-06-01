CREATE TABLE recipes (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    instructions TEXT NOT NULL,
    base_servings INT NOT NULL,
    cost NUMERIC(19, 4) NOT NULL DEFAULT 0
);

CREATE TABLE ingredients (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    base_amount DOUBLE PRECISION NOT NULL,
    unit VARCHAR(50) NOT NULL
);

CREATE TABLE food_calendar (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL,
    recipe_id BIGINT NOT NULL REFERENCES recipes(id) ON DELETE CASCADE
);

CREATE TABLE food_ratings (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uk_food_ratings_user_recipe UNIQUE (user_id, recipe_id)
);

CREATE INDEX idx_food_calendar_date ON food_calendar(date);
CREATE INDEX idx_food_calendar_recipe_id ON food_calendar(recipe_id);
CREATE INDEX idx_food_ratings_recipe_id ON food_ratings(recipe_id);
