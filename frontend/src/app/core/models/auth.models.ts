export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  username: string;
  role: string;
  fullName?: string;
}
