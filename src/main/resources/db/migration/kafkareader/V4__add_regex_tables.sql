CREATE TABLE regex_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE regex_patterns (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    pattern VARCHAR(1024) NOT NULL,
    regex_group_id BIGINT,
    FOREIGN KEY (regex_group_id) REFERENCES regex_groups(id)
);