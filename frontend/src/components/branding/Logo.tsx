import { ShoppingCart } from "lucide-react";

const Logo = () => {
  return (
    <div className="flex flex-col items-center">

      <div className="flex items-center gap-5">

        {/* Icon */}

        <div className="relative">

          <ShoppingCart
            size={78}
            strokeWidth={2.2}
            className="text-[#0F172A]"
          />

          <svg
            className="absolute -bottom-5 left-0"
            width="90"
            height="34"
            viewBox="0 0 90 34"
            fill="none"
          >
            <path
              d="M3 18C18 33 55 33 83 16"
              stroke="#FF9900"
              strokeWidth="5"
              strokeLinecap="round"
            />

            <path
              d="M76 9L84 16L76 24"
              stroke="#FF9900"
              strokeWidth="5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>

        </div>

        {/* Text */}

        <div>

          <h1 className="font-black tracking-tight leading-none">

            <span className="text-[#0F172A] text-6xl">
              Shop
            </span>

            <span className="text-[#FF9900] text-6xl">
              Sphere
            </span>

          </h1>

          <div className="mt-3 flex items-center gap-4">

            <div className="h-[2px] w-20 bg-gray-400 rounded-full" />

            <p className="text-lg tracking-[0.35em] text-gray-500">
              Smart Commerce Platform
            </p>

            <div className="h-[2px] w-20 bg-gray-400 rounded-full" />

          </div>

        </div>

      </div>

    </div>
  );
};

export default Logo;