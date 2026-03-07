import { useState } from "react";
import { Coffee, ShoppingBag, Gift, CreditCard, Search } from "lucide-react";
import { usePoints } from "../contexts/PointsContext";
import { motion, AnimatePresence } from "motion/react";

interface Product {
  id: string;
  name: string;
  brand: string;
  points: number;
  image: string;
  category: string;
}

const products: Product[] = [
  {
    id: "1",
    name: "아메리카노 Tall",
    brand: "스타벅스",
    points: 4500,
    image: "☕",
    category: "cafe",
  },
  {
    id: "2",
    name: "카페라떼 Grande",
    brand: "스타벅스",
    points: 5500,
    image: "☕",
    category: "cafe",
  },
  {
    id: "3",
    name: "1+1 교환권",
    brand: "CU",
    points: 3000,
    image: "🏪",
    category: "cvs",
  },
  {
    id: "4",
    name: "해피콘 3,000원권",
    brand: "배스킨라빈스",
    points: 3000,
    image: "🍦",
    category: "cafe",
  },
  {
    id: "5",
    name: "치킨 할인권 5,000원",
    brand: "BBQ",
    points: 5000,
    image: "🍗",
    category: "food",
  },
  {
    id: "6",
    name: "문화상품권 5,000원",
    brand: "문화상품권",
    points: 5000,
    image: "🎁",
    category: "voucher",
  },
];

type Category = "all" | "cafe" | "cvs" | "food" | "voucher";

export function ShopScreen() {
  const { points, spendPoints } = usePoints();
  const [selectedCategory, setSelectedCategory] = useState<Category>("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [showPurchaseModal, setShowPurchaseModal] = useState(false);
  const [showInsufficientModal, setShowInsufficientModal] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);

  const categories = [
    { id: "all" as Category, label: "전체", icon: ShoppingBag },
    { id: "cafe" as Category, label: "카페", icon: Coffee },
    { id: "cvs" as Category, label: "편의점", icon: ShoppingBag },
    { id: "food" as Category, label: "외식", icon: Gift },
    { id: "voucher" as Category, label: "상품권", icon: CreditCard },
  ];

  const filteredProducts =
    selectedCategory === "all"
      ? products
      : products.filter((p) => p.category === selectedCategory);
  const searchedProducts = filteredProducts.filter((product) => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return true;
    return (
      product.name.toLowerCase().includes(query) ||
      product.brand.toLowerCase().includes(query)
    );
  });

  const handlePurchase = (product: Product) => {
    setSelectedProduct(product);
    if (points >= product.points) {
      setShowPurchaseModal(true);
    } else {
      setShowInsufficientModal(true);
    }
  };

  const confirmPurchase = () => {
    if (selectedProduct && spendPoints(selectedProduct.points)) {
      setShowPurchaseModal(false);
      setSelectedProduct(null);
    }
  };

  return (
    <div className="h-full overflow-y-auto bg-[#F4F6F8]">
      {/* Header */}
      <header className="bg-white px-4 py-6">
        <div className="flex items-center justify-between mb-4">
          <h1 className="text-2xl font-bold text-gray-900">포인트 상점</h1>
          <div className="bg-[#5C6BFA] text-white px-4 py-2 rounded-full">
            <span className="font-bold">🪙 {points.toLocaleString()} P</span>
          </div>
        </div>

        {/* Search Bar */}
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
          <input
            type="text"
            placeholder="상품을 검색하세요"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full bg-[#F4F6F8] rounded-xl pl-10 pr-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-[#5C6BFA]"
          />
        </div>
      </header>

      {/* Categories */}
      <div className="bg-white px-4 py-3 border-b border-gray-200 overflow-x-auto">
        <div className="flex gap-2">
          {categories.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              onClick={() => setSelectedCategory(id)}
              className={`flex items-center gap-2 px-4 py-2 rounded-full whitespace-nowrap transition-colors ${
                selectedCategory === id
                  ? "bg-[#5C6BFA] text-white"
                  : "bg-[#F4F6F8] text-gray-600"
              }`}
            >
              <Icon className="w-4 h-4" />
              {label}
            </button>
          ))}
        </div>
      </div>

      {/* Products Grid */}
      <div className="p-4 grid grid-cols-2 gap-3">
        {searchedProducts.map((product) => (
          <motion.div
            key={product.id}
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            className="bg-white rounded-2xl overflow-hidden shadow-sm"
          >
            <div className="aspect-square bg-gradient-to-br from-[#F4F6F8] to-[#E8EAF6] flex items-center justify-center">
              <span className="text-6xl">{product.image}</span>
            </div>
            <div className="p-3">
              <p className="text-xs text-gray-500 mb-1">{product.brand}</p>
              <h3 className="font-semibold text-sm text-gray-900 mb-2 line-clamp-2">
                {product.name}
              </h3>
              <div className="flex items-center justify-between">
                <span className="text-[#FF6B00] font-bold">
                  🪙 {product.points.toLocaleString()} P
                </span>
              </div>
              <button
                onClick={() => handlePurchase(product)}
                className="w-full bg-[#5C6BFA] text-white py-2 rounded-xl font-semibold text-sm mt-2"
              >
                구매하기
              </button>
            </div>
          </motion.div>
        ))}
      </div>

      {/* Purchase Confirmation Modal */}
      <AnimatePresence>
        {showPurchaseModal && selectedProduct && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
            onClick={() => setShowPurchaseModal(false)}
          >
            <motion.div
              initial={{ scale: 0.9, y: 20 }}
              animate={{ scale: 1, y: 0 }}
              exit={{ scale: 0.9, y: 20 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-white rounded-2xl p-6 max-w-sm w-full"
            >
              <h3 className="text-xl font-bold text-gray-900 mb-2">구매 확인</h3>
              <p className="text-gray-600 mb-4">
                {selectedProduct.brand} {selectedProduct.name}을(를) 구매하시겠습니까?
              </p>
              <div className="bg-[#F4F6F8] rounded-xl p-4 mb-4">
                <div className="flex justify-between mb-2">
                  <span className="text-gray-600">현재 포인트</span>
                  <span className="font-bold">{points.toLocaleString()} P</span>
                </div>
                <div className="flex justify-between mb-2">
                  <span className="text-gray-600">사용 포인트</span>
                  <span className="font-bold text-[#FF6B00]">
                    -{selectedProduct.points.toLocaleString()} P
                  </span>
                </div>
                <div className="border-t border-gray-300 pt-2 mt-2">
                  <div className="flex justify-between">
                    <span className="font-bold">남은 포인트</span>
                    <span className="font-bold text-[#5C6BFA]">
                      {(points - selectedProduct.points).toLocaleString()} P
                    </span>
                  </div>
                </div>
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => setShowPurchaseModal(false)}
                  className="flex-1 bg-gray-200 text-gray-700 py-3 rounded-xl font-semibold"
                >
                  취소
                </button>
                <button
                  onClick={confirmPurchase}
                  className="flex-1 bg-[#5C6BFA] text-white py-3 rounded-xl font-semibold"
                >
                  구매하기
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Insufficient Points Modal */}
      <AnimatePresence>
        {showInsufficientModal && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
            onClick={() => setShowInsufficientModal(false)}
          >
            <motion.div
              initial={{ scale: 0.9, y: 20 }}
              animate={{ scale: 1, y: 0 }}
              exit={{ scale: 0.9, y: 20 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-white rounded-2xl p-6 max-w-sm w-full text-center"
            >
              <div className="text-6xl mb-4">😢</div>
              <h3 className="text-xl font-bold text-gray-900 mb-2">포인트가 부족해요!</h3>
              <p className="text-gray-600 mb-4">
                10초 광고를 보고 포인트를 더 모으시겠어요?
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => setShowInsufficientModal(false)}
                  className="flex-1 bg-gray-200 text-gray-700 py-3 rounded-xl font-semibold"
                >
                  닫기
                </button>
                <button
                  onClick={() => setShowInsufficientModal(false)}
                  className="flex-1 bg-[#FF6B00] text-white py-3 rounded-xl font-semibold"
                >
                  광고 보기
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
