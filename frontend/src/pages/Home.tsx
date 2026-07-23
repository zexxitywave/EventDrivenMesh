import { useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import { LogOut, User, Mail, Shield } from "lucide-react";

import { useAuth } from "../context/AuthContext";

const Home = () => {
  const navigate = useNavigate();

  const { user, logout } = useAuth();

  const handleLogout = () => {
    logout();
    toast.success("Logged Out");
    navigate("/login");
  };

  return (
    <div className="min-h-screen bg-slate-100 flex items-center justify-center p-8">
      <div className="w-full max-w-xl rounded-3xl bg-white shadow-2xl p-10">

        <div className="flex items-center justify-between">

          <div>
            <h1 className="text-3xl font-bold text-slate-900">
              Dashboard
            </h1>

            <p className="mt-2 text-slate-500">
              Welcome back!
            </p>
          </div>

          <button
            onClick={handleLogout}
            className="flex items-center gap-2 rounded-xl bg-red-500 px-4 py-2 text-white transition hover:bg-red-600"
          >
            <LogOut size={18} />
            Logout
          </button>

        </div>

        <div className="mt-10 space-y-5">

          <div className="flex items-center gap-4 rounded-xl border p-4">

            <User className="text-indigo-600" />

            <div>
              <p className="text-sm text-slate-500">
                Name
              </p>

              <p className="font-semibold">
                {user?.name}
              </p>
            </div>

          </div>

          <div className="flex items-center gap-4 rounded-xl border p-4">

            <Mail className="text-indigo-600" />

            <div>
              <p className="text-sm text-slate-500">
                Email
              </p>

              <p className="font-semibold">
                {user?.email}
              </p>
            </div>

          </div>

          <div className="flex items-center gap-4 rounded-xl border p-4">

            <Shield className="text-indigo-600" />

            <div>
              <p className="text-sm text-slate-500">
                Role
              </p>

              <p className="font-semibold capitalize">
                {user?.role}
              </p>
            </div>

          </div>

        </div>

      </div>
    </div>
  );
};

export default Home;