import request from 'supertest';
import { app } from '../app';
import { createTestUser } from '../test/setup';
import bcrypt from 'bcrypt';
describe('POST /api/auth/login', () => {
    it('should login with valid credentials', async () => {
        const password = 'password123';
        const hashedPassword = await bcrypt.hash(password, 10);
        const user = await createTestUser({
            email: 'test@example.com',
            password: hashedPassword,
        });
        const response = await request(app)
            .post('/api/auth/login')
            .send({
            email: 'test@example.com',
            password: 'password123',
        })
            .expect(200);
        expect(response.body).toMatchObject({
            user: {
                id: user.id,
                email: user.email,
                name: user.name,
                role: user.role,
            },
            accessToken: expect.any(String),
            refreshToken: expect.any(String),
        });
        expect(response.body.user.password).toBeUndefined();
    });
    it('should return 400 with invalid email format', async () => {
        const response = await request(app)
            .post('/api/auth/login')
            .send({
            email: 'invalid-email',
            password: 'password123',
        })
            .expect(400);
        expect(response.body.error).toBe('Invalid email or password');
    });
    it('should return 400 with missing password', async () => {
        const response = await request(app)
            .post('/api/auth/login')
            .send({
            email: 'test@example.com',
        })
            .expect(400);
        expect(response.body.error).toBe('Email and password are required');
    });
    it('should return 400 with wrong password', async () => {
        const password = 'password123';
        const hashedPassword = await bcrypt.hash(password, 10);
        await createTestUser({
            email: 'test@example.com',
            password: hashedPassword,
        });
        const response = await request(app)
            .post('/api/auth/login')
            .send({
            email: 'test@example.com',
            password: 'wrongpassword',
        })
            .expect(400);
        expect(response.body.error).toBe('Invalid email or password');
    });
    it('should return 400 with non-existent user', async () => {
        const response = await request(app)
            .post('/api/auth/login')
            .send({
            email: 'nonexistent@example.com',
            password: 'password123',
        })
            .expect(400);
        expect(response.body.error).toBe('Invalid email or password');
    });
});
describe('POST /api/auth/register', () => {
    it('should register new user with valid data', async () => {
        const response = await request(app)
            .post('/api/auth/register')
            .send({
            name: 'Test User',
            email: 'newuser@example.com',
            password: 'password123',
        })
            .expect(201);
        expect(response.body).toMatchObject({
            user: {
                name: 'Test User',
                email: 'newuser@example.com',
                role: 'USER',
            },
            accessToken: expect.any(String),
            refreshToken: expect.any(String),
        });
        expect(response.body.user.password).toBeUndefined();
    });
    it('should return 400 with existing email', async () => {
        await createTestUser({
            email: 'existing@example.com',
        });
        const response = await request(app)
            .post('/api/auth/register')
            .send({
            name: 'Test User',
            email: 'existing@example.com',
            password: 'password123',
        })
            .expect(400);
        expect(response.body.error).toBe('User already exists');
    });
    it('should return 400 with weak password', async () => {
        const response = await request(app)
            .post('/api/auth/register')
            .send({
            name: 'Test User',
            email: 'test@example.com',
            password: '123',
        })
            .expect(400);
        expect(response.body.error).toBe('Password must be at least 6 characters');
    });
    it('should return 400 with invalid email', async () => {
        const response = await request(app)
            .post('/api/auth/register')
            .send({
            name: 'Test User',
            email: 'invalid-email',
            password: 'password123',
        })
            .expect(400);
        expect(response.body.error).toContain('Invalid email');
    });
    it('should return 400 with missing required fields', async () => {
        const response = await request(app)
            .post('/api/auth/register')
            .send({
            email: 'test@example.com',
        })
            .expect(400);
        expect(response.body.error).toContain('required');
    });
});
describe('POST /api/auth/refresh', () => {
    it('should refresh access token with valid refresh token', async () => {
        const user = await createTestUser({
            email: 'test@example.com',
            password: await bcrypt.hash('password123', 10),
        });
        const loginResponse = await request(app)
            .post('/api/auth/login')
            .send({
            email: 'test@example.com',
            password: 'password123',
        });
        const { refreshToken } = loginResponse.body;
        const response = await request(app)
            .post('/api/auth/refresh')
            .send({ refreshToken })
            .expect(200);
        expect(response.body).toMatchObject({
            accessToken: expect.any(String),
            refreshToken: expect.any(String),
        });
    });
    it('should return 401 with invalid refresh token', async () => {
        const response = await request(app)
            .post('/api/auth/refresh')
            .send({ refreshToken: 'invalid-token' })
            .expect(401);
        expect(response.body.error).toBe('Invalid refresh token');
    });
    it('should return 400 with missing refresh token', async () => {
        const response = await request(app)
            .post('/api/auth/refresh')
            .send({})
            .expect(400);
        expect(response.body.error).toBe('Refresh token is required');
    });
});
describe('POST /api/auth/logout', () => {
    it('should logout and invalidate refresh token', async () => {
        const user = await createTestUser({
            email: 'test@example.com',
            password: await bcrypt.hash('password123', 10),
        });
        const loginResponse = await request(app)
            .post('/api/auth/login')
            .send({
            email: 'test@example.com',
            password: 'password123',
        });
        const { refreshToken } = loginResponse.body;
        const response = await request(app)
            .post('/api/auth/logout')
            .send({ refreshToken })
            .expect(200);
        expect(response.body.message).toBe('Logged out successfully');
        await request(app)
            .post('/api/auth/refresh')
            .send({ refreshToken })
            .expect(401);
    });
    it('should return 400 with missing refresh token', async () => {
        const response = await request(app)
            .post('/api/auth/logout')
            .send({})
            .expect(400);
        expect(response.body.error).toBe('Refresh token is required');
    });
});
//# sourceMappingURL=auth.test.js.map