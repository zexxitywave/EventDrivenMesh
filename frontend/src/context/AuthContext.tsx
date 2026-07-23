import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import type { AuthResponse } from "../types/auth";
import { tokenService } from "../services/auth/tokenService";

interface AuthContextType {
  user: AuthResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (auth: AuthResponse) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(
  undefined
);

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider = ({
  children,
}: AuthProviderProps) => {
  const [user, setUser] = useState<AuthResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const accessToken = tokenService.getAccessToken();
    const refreshToken = tokenService.getRefreshToken();
    const storedUser = tokenService.getUser();

    if (
      accessToken &&
      refreshToken &&
      storedUser
    ) {
      setUser({
        accessToken,
        refreshToken,
        tokenType: "Bearer",
        expiresIn: 0,
        userId: storedUser.userId,
        name: storedUser.name,
        email: storedUser.email,
        role: storedUser.role,
      });
    }

    setIsLoading(false);
  }, []);

  const login = (auth: AuthResponse) => {
    tokenService.setAccessToken(auth.accessToken);

    tokenService.setRefreshToken(auth.refreshToken);

    tokenService.setUser({
      userId: auth.userId,
      name: auth.name,
      email: auth.email,
      role: auth.role,
    });

    setUser(auth);
  };

  const logout = () => {
    tokenService.clear();

    setUser(null);
  };

  const value = useMemo(
    () => ({
      user,
      isAuthenticated: !!user,
      isLoading,
      login,
      logout,
    }),
    [user, isLoading]
  );

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error(
      "useAuth must be used inside AuthProvider"
    );
  }

  return context;
};