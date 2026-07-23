import type { ReactNode } from "react";
import logo from "../assets/logo.svg";
interface AuthLayoutProps {
  children: ReactNode;
}

const AuthLayout = ({ children }: AuthLayoutProps) => {
  return (
    <div className="min-h-screen flex flex-col bg-[#f5f6f8]">

      {/* Main Content */}

      <main className="flex flex-1 justify-center px-6 py-8">
  <div className="flex w-full max-w-xl flex-col items-center">

    {/* Logo */}
    <img
      src={logo}
      alt="ShopSphere"
      className="mb-4 w-[340px] h-auto"
    />

    {/* Card */}
    {children}

  </div>
</main>

      {/* Footer */}

      <footer className="border-t border-gray-200 bg-white">

        <div className="mx-auto flex max-w-6xl flex-col items-center gap-4 px-6 py-5">

          <div className="flex flex-wrap justify-center gap-6 text-sm">

            <button
              type="button"
              className="text-[#0066c0] hover:underline"
            >
              Conditions of Use
            </button>

            <button
              type="button"
              className="text-[#0066c0] hover:underline"
            >
              Privacy Policy
            </button>

            <button
              type="button"
              className="text-[#0066c0] hover:underline"
            >
              Contact Support
            </button>

          </div>

          <p className="text-center text-xs text-gray-500">
            © 2026 ShopSphere Technologies Pvt. Ltd.
            <br />
            Built with React • TypeScript • Spring Boot
          </p>

        </div>

      </footer>

    </div>
  );
};

export default AuthLayout;